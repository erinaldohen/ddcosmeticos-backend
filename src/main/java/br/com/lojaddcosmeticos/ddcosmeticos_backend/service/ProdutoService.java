package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Slf4j
@Service
@Transactional
public class ProdutoService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CalculadoraFiscalService calculadoraFiscalService;
    @Autowired private AuditoriaService auditoriaService;

    // ==================================================================================
    // 1. IMPORTAÇÃO (ROTEADOR E LÓGICA)
    // ==================================================================================

    // Roteador: Recebe o arquivo e decide qual método chamar
    public String importarProdutos(MultipartFile file) {
        if (file.isEmpty()) return "Arquivo vazio";

        String nomeArquivo = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        try {
            if (nomeArquivo.endsWith(".csv")) {
                return importarProdutosViaCsv(file.getInputStream());
            } else if (nomeArquivo.endsWith(".xls") || nomeArquivo.endsWith(".xlsx")) {
                return importarProdutosViaExcel(file.getInputStream());
            } else {
                return "Formato não suportado. Use .csv, .xls ou .xlsx";
            }
        } catch (Exception e) {
            return "Erro ao abrir arquivo: " + e.getMessage();
        }
    }

    // Alias para manter compatibilidade com DataSeeder (que usa CSV)
    public String importarProdutosViaInputStream(InputStream is) {
        return importarProdutosViaCsv(is);
    }

    // --- IMPORTAÇÃO VIA EXCEL (XLS/XLSX) ---

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String importarProdutosViaExcel(InputStream inputStream) {
        List<Produto> loteParaSalvar = new ArrayList<>();
        Set<String> eansNoLote = new HashSet<>();
        List<String> erros = new ArrayList<>();

        int totalSalvo = 0;
        int linhaAtual = 0;
        int TAMANHO_LOTE = 50;

        Map<String, Integer> mapaColunas = new HashMap<>();
        boolean cabecalhoProcessado = false;

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0); // Pega primeira aba

            for (Row row : sheet) {
                linhaAtual++;
                if (row == null || isRowEmpty(row)) continue;

                // FASE 1: MAPEAMENTO (Linha 1)
                if (!cabecalhoProcessado) {
                    for (Cell cell : row) {
                        String col = normalizarString(getCellValue(cell));
                        int idx = cell.getColumnIndex();

                        if (col.contains("EAN") || col.contains("BARRAS") || col.contains("GTIN") || col.contains("CODIGO")) {
                            if (!mapaColunas.containsKey("EAN")) mapaColunas.put("EAN", idx);
                        } else if (col.contains("CUSTO")) {
                            mapaColunas.put("PRECO", idx);
                        } else if ((col.contains("PRECO") || col.contains("VALOR")) && !mapaColunas.containsKey("PRECO")) {
                            mapaColunas.put("PRECO", idx);
                        } else if (col.contains("DESCRICAO")) {
                            mapaColunas.put("DESC", idx);
                        } else if ((col.contains("NOME") || col.contains("PRODUTO")) && !mapaColunas.containsKey("DESC")) {
                            if (!col.contains("CATEGORIA") && !col.contains("MARCA")) mapaColunas.put("DESC", idx);
                        }
                    }

                    // Fallback
                    if (!mapaColunas.containsKey("EAN")) mapaColunas.put("EAN", 0);
                    if (!mapaColunas.containsKey("DESC")) mapaColunas.put("DESC", 1);
                    if (!mapaColunas.containsKey("PRECO")) mapaColunas.put("PRECO", 2);

                    cabecalhoProcessado = true;
                    continue;
                }

                // FASE 2: DADOS
                try {
                    String ean = tratarEan(getCellValue(row.getCell(mapaColunas.get("EAN"), MissingCellPolicy.RETURN_BLANK_AS_NULL)));
                    if (ean.isEmpty()) continue;

                    if (eansNoLote.contains(ean)) {
                        erros.add("Linha " + linhaAtual + ": EAN " + ean + " duplicado no arquivo.");
                        continue;
                    }

                    String desc = limparTexto(getCellValue(row.getCell(mapaColunas.get("DESC"), MissingCellPolicy.RETURN_BLANK_AS_NULL))).toUpperCase();

                    String valPreco = getCellValue(row.getCell(mapaColunas.get("PRECO"), MissingCellPolicy.RETURN_BLANK_AS_NULL));
                    BigDecimal custo;
                    try {
                        custo = limparEConverterMoeda(valPreco);
                    } catch (Exception e) {
                        throw new Exception("Preço inválido: '" + valPreco + "'");
                    }

                    Produto p = prepararProdutoParaLote(ean, desc, custo);
                    loteParaSalvar.add(p);
                    eansNoLote.add(ean);

                    if (loteParaSalvar.size() >= TAMANHO_LOTE) {
                        totalSalvo += processarLoteSeguro(loteParaSalvar, erros);
                        loteParaSalvar.clear();
                        eansNoLote.clear();
                    }
                } catch (Exception e) {
                    erros.add("Linha " + linhaAtual + ": " + e.getMessage());
                }
            }
            if (!loteParaSalvar.isEmpty()) totalSalvo += processarLoteSeguro(loteParaSalvar, erros);

        } catch (Exception e) {
            return "Erro ao ler Excel: " + e.getMessage();
        }

        return gerarRelatorioImportacao(totalSalvo, erros);
    }

    // --- IMPORTAÇÃO VIA CSV (O método robusto anterior) ---
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String importarProdutosViaCsv(InputStream inputStream) {
        List<Produto> loteParaSalvar = new ArrayList<>();
        Set<String> eansNoLote = new HashSet<>();
        List<String> erros = new ArrayList<>();

        int totalSalvo = 0;
        int linhaAtual = 0;
        int TAMANHO_LOTE = 50;
        Map<String, Integer> mapaColunas = new HashMap<>();
        boolean cabecalhoProcessado = false;
        String delimitador = ";";

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                linhaAtual++;
                if (linha.trim().isEmpty()) continue;

                if (!cabecalhoProcessado) {
                    int countPV = linha.length() - linha.replace(";", "").length();
                    int countV = linha.length() - linha.replace(",", "").length();
                    if (countV > countPV) delimitador = ",";

                    String[] headers = parseCsvLine(linha, delimitador);
                    for (int i = 0; i < headers.length; i++) {
                        String col = normalizarString(headers[i]);
                        if (col.contains("EAN") || col.contains("BARRAS") || col.contains("GTIN") || col.contains("CODIGO")) {
                            if (!mapaColunas.containsKey("EAN")) mapaColunas.put("EAN", i);
                        } else if (col.contains("CUSTO")) {
                            mapaColunas.put("PRECO", i);
                        } else if ((col.contains("PRECO") || col.contains("VALOR")) && !mapaColunas.containsKey("PRECO")) {
                            mapaColunas.put("PRECO", i);
                        } else if (col.contains("DESCRICAO")) {
                            mapaColunas.put("DESC", i);
                        } else if ((col.contains("NOME") || col.contains("PRODUTO")) && !mapaColunas.containsKey("DESC")) {
                            if (!col.contains("CATEGORIA") && !col.contains("MARCA")) mapaColunas.put("DESC", i);
                        }
                    }
                    if (!mapaColunas.containsKey("EAN")) mapaColunas.put("EAN", 0);
                    if (!mapaColunas.containsKey("DESC")) mapaColunas.put("DESC", 1);
                    if (!mapaColunas.containsKey("PRECO")) mapaColunas.put("PRECO", 2);
                    cabecalhoProcessado = true;
                    continue;
                }

                try {
                    String[] dados = parseCsvLine(linha, delimitador);
                    int maxIdx = Collections.max(mapaColunas.values());
                    if (dados.length <= maxIdx) throw new Exception("Linha incompleta.");

                    String ean = tratarEan(getSafe(dados, mapaColunas.get("EAN")));
                    if (ean.isEmpty()) throw new Exception("EAN vazio.");
                    if (eansNoLote.contains(ean)) {
                        erros.add("Linha " + linhaAtual + ": EAN " + ean + " duplicado no arquivo.");
                        continue;
                    }

                    String desc = limparTexto(getSafe(dados, mapaColunas.get("DESC"))).toUpperCase();
                    String valPreco = getSafe(dados, mapaColunas.get("PRECO"));
                    BigDecimal custo;
                    try { custo = limparEConverterMoeda(valPreco); }
                    catch (Exception e) { throw new Exception("Preço inválido: '" + valPreco + "'"); }

                    Produto p = prepararProdutoParaLote(ean, desc, custo);
                    loteParaSalvar.add(p);
                    eansNoLote.add(ean);

                    if (loteParaSalvar.size() >= TAMANHO_LOTE) {
                        totalSalvo += processarLoteSeguro(loteParaSalvar, erros);
                        loteParaSalvar.clear();
                        eansNoLote.clear();
                    }
                } catch (Exception e) {
                    erros.add("Linha " + linhaAtual + ": " + e.getMessage());
                }
            }
            if (!loteParaSalvar.isEmpty()) totalSalvo += processarLoteSeguro(loteParaSalvar, erros);
        } catch (Exception e) {
            return "Erro crítico ao ler CSV: " + e.getMessage();
        }
        return gerarRelatorioImportacao(totalSalvo, erros);
    }

    // ==================================================================================
    // 2. EXPORTAÇÃO (CSV E EXCEL)
    // ==================================================================================

    public byte[] gerarRelatorioCsv() {
        List<Produto> produtos = produtoRepository.findAll();
        StringBuilder sb = new StringBuilder();
        sb.append("ID;EAN;DESCRIÇÃO;PREÇO CUSTO;PREÇO VENDA;ESTOQUE;NCM\n");

        for (Produto p : produtos) {
            sb.append(p.getId()).append(";");
            sb.append(p.getCodigoBarras() != null ? p.getCodigoBarras() : "").append(";");
            sb.append(p.getDescricao().replace(";", ",")).append(";");
            sb.append(p.getPrecoCusto()).append(";");
            sb.append(p.getPrecoVenda()).append(";");
            sb.append(p.getQuantidadeEmEstoque()).append(";");
            sb.append(p.getNcm() != null ? p.getNcm() : "").append("\n");
        }
        // ISO-8859-1 para Excel abrir direto com acentos no Windows
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    public byte[] gerarRelatorioExcel() {
        List<Produto> produtos = produtoRepository.findAll();
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Estoque Atual");

            // Estilo Cabeçalho
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            String[] cols = {"ID", "EAN", "DESCRIÇÃO", "MARCA", "CUSTO", "VENDA", "ESTOQUE", "NCM"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Produto p : produtos) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getCodigoBarras());
                row.createCell(2).setCellValue(p.getDescricao());
                row.createCell(3).setCellValue(p.getMarca());

                Cell cellCusto = row.createCell(4);
                cellCusto.setCellValue(p.getPrecoCusto() != null ? p.getPrecoCusto().doubleValue() : 0.0);

                Cell cellVenda = row.createCell(5);
                cellVenda.setCellValue(p.getPrecoVenda() != null ? p.getPrecoVenda().doubleValue() : 0.0);

                row.createCell(6).setCellValue(p.getQuantidadeEmEstoque());
                row.createCell(7).setCellValue(p.getNcm());
            }

            for(int i=0; i<cols.length; i++) sheet.autoSizeColumn(i);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar Excel: " + e.getMessage());
        }
    }

    // ==================================================================================
    // 3. UTILITÁRIOS E HELPERS
    // ==================================================================================

    private Produto prepararProdutoParaLote(String ean, String desc, BigDecimal custo) {
        Produto p;
        Optional<Produto> existente = produtoRepository.findByCodigoBarras(ean);
        if (existente.isPresent()) {
            p = existente.get();
        } else {
            p = new Produto();
            p.setCodigoBarras(ean);
            p.setQuantidadeEmEstoque(0);
            p.setEstoqueFiscal(0);
            p.setEstoqueNaoFiscal(0);
            p.setAtivo(true);
            p.setNcm("00000000");
            p.setOrigem("0");
            p.setCst("102");
        }
        p.setDescricao(desc);
        p.setPrecoCusto(custo);
        p.setPrecoMedioPonderado(custo);
        if (p.getPrecoVenda() == null || p.getPrecoVenda().compareTo(custo.multiply(new BigDecimal("1.1"))) < 0) {
            p.setPrecoVenda(custo.multiply(new BigDecimal("1.5")));
        }
        return p;
    }

    private int processarLoteSeguro(List<Produto> lote, List<String> erros) {
        try {
            produtoRepository.saveAll(lote);
            produtoRepository.flush();
            return lote.size();
        } catch (Exception e) {
            int salvosNoFallback = 0;
            for (Produto pLote : lote) {
                try {
                    Produto pFresco = prepararProdutoParaLote(pLote.getCodigoBarras(), pLote.getDescricao(), pLote.getPrecoCusto());
                    produtoRepository.save(pFresco);
                    salvosNoFallback++;
                } catch (Exception ex) {
                    erros.add("Erro ao salvar '" + pLote.getDescricao() + "': " + ex.getMessage());
                }
            }
            return salvosNoFallback;
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                DataFormatter fmt = new DataFormatter();
                return fmt.formatCellValue(cell); // Evita notação científica no Excel
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue(); }
                catch (Exception e) { return String.valueOf(cell.getNumericCellValue()); }
            default: return "";
        }
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellValue(cell).trim().isEmpty())
                return false;
        }
        return true;
    }

    private String gerarRelatorioImportacao(int total, List<String> erros) {
        StringBuilder sb = new StringBuilder();
        sb.append("Importação Concluída!\n");
        sb.append("✅ Sucesso: ").append(total).append("\n");
        sb.append("❌ Erros: ").append(erros.size()).append("\n");
        if (!erros.isEmpty()) {
            sb.append("\n--- Detalhes (Primeiros 10) ---\n");
            erros.stream().limit(10).forEach(e -> sb.append(e).append("\n"));
        }
        return sb.toString();
    }

    private String getSafe(String[] arr, int index) {
        if (index >= arr.length) return "";
        return arr[index];
    }

    private String normalizarString(String str) {
        if (str == null) return "";
        str = str.replace("\uFEFF", "");
        String nfd = Normalizer.normalize(str, Normalizer.Form.NFD);
        String semAcento = nfd.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return semAcento.toUpperCase().trim();
    }

    private String[] parseCsvLine(String line, String delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '\"') inQuotes = !inQuotes;
            else if (String.valueOf(c).equals(delimiter) && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else sb.append(c);
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }

    private String tratarEan(String eanBruto) {
        if (eanBruto == null) return "";
        String limpo = eanBruto.replaceAll("\"", "").replace("'", "").trim();
        if (limpo.toUpperCase().contains("E+")) {
            try { return new BigDecimal(limpo.replace(",", ".")).toPlainString(); }
            catch (Exception e) { return limpo; }
        }
        return limpo;
    }

    private BigDecimal limparEConverterMoeda(String valorBruto) {
        if (valorBruto == null || valorBruto.trim().isEmpty()) return BigDecimal.ZERO;
        String limpo = valorBruto.replace("R$", "").trim();
        limpo = limpo.replaceAll("[^0-9.,-]", "");
        if (limpo.isEmpty()) return BigDecimal.ZERO;
        if (limpo.contains(",")) limpo = limpo.replace(".", "").replace(",", ".");
        else if (limpo.chars().filter(ch -> ch == '.').count() > 1) limpo = limpo.replace(".", "");
        return new BigDecimal(limpo);
    }

    private String limparTexto(String texto) {
        if (texto == null) return "";
        return texto.replaceAll("\"", "").trim();
    }

    // --- MÉTODOS ORIGINAIS (CRUD e FISCAL) ---
    @Transactional
    public void processarEntradaEstoque(Produto produto, Integer quantidadeEntrada, BigDecimal custoEntrada) {
        if (quantidadeEntrada <= 0) return;
        BigDecimal estoqueAtual = new BigDecimal(produto.getQuantidadeEmEstoque());
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;
        BigDecimal valorTotalAtual = estoqueAtual.multiply(custoMedioAtual);
        BigDecimal valorTotalEntrada = custoEntrada.multiply(new BigDecimal(quantidadeEntrada));
        BigDecimal novoValorTotal = valorTotalAtual.add(valorTotalEntrada);
        BigDecimal novaQuantidade = estoqueAtual.add(new BigDecimal(quantidadeEntrada));
        if (novaQuantidade.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal novoPrecoMedio = novoValorTotal.divide(novaQuantidade, 4, RoundingMode.HALF_UP);
            produto.setPrecoMedioPonderado(novoPrecoMedio);
        } else {
            produto.setPrecoMedioPonderado(custoEntrada);
        }
        produto.setPrecoCusto(custoEntrada);
    }

    @Transactional(readOnly = true)
    public Page<ProdutoListagemDTO> listarResumo(String termo, Pageable pageable) {
        if (pageable == null) pageable = Pageable.unpaged();
        Page<Produto> pagina;
        if (termo == null || termo.isBlank()) {
            pagina = produtoRepository.findAll(pageable);
        } else {
            pagina = produtoRepository.findByDescricaoContainingIgnoreCaseOrCodigoBarras(termo, termo, pageable);
        }
        return pagina.map(p -> new ProdutoListagemDTO(
                p.getId(), p.getDescricao(), p.getPrecoVenda(), p.getUrlImagem(),
                p.getQuantidadeEmEstoque(), p.isAtivo(), p.getCodigoBarras(),
                p.getMarca(), p.getNcm()
        ));
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Produto> listarBaixoEstoque() {
        return produtoRepository.findProdutosComBaixoEstoque();
    }

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistorico(Long id) {
        return auditoriaService.buscarHistoricoDoProduto(id);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoDTO salvar(ProdutoDTO dto) {
        if (produtoRepository.existsByCodigoBarras(dto.codigoBarras())) {
            var existente = produtoRepository.findByEanIrrestrito(dto.codigoBarras());
            if(existente.isPresent() && !existente.get().isAtivo()) {
                throw new ValidationException("Produto existe mas está inativo. Reative-o.");
            }
            throw new ValidationException("Já existe um produto com este código de barras.");
        }
        Produto produto = new Produto();
        copiarDtoParaEntidade(dto, produto);
        BigDecimal custoInicial = dto.precoCusto() != null ? dto.precoCusto() : BigDecimal.ZERO;
        produto.setPrecoMedioPonderado(custoInicial);
        produto.setQuantidadeEmEstoque(0);
        produto.setEstoqueFiscal(0);
        produto.setEstoqueNaoFiscal(0);
        produto.setAtivo(true);
        produto = produtoRepository.save(produto);
        return new ProdutoDTO(produto);
    }

    @Transactional
    public Produto salvar(Produto produto) {
        return produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Produto atualizar(Long id, ProdutoDTO dados) {
        Produto produto = buscarPorId(id);
        if (dados.codigoBarras() != null && !dados.codigoBarras().equals(produto.getCodigoBarras())) {
            if (produtoRepository.existsByCodigoBarras(dados.codigoBarras())) {
                throw new ValidationException("Já existe outro produto com este EAN.");
            }
        }
        copiarDtoParaEntidade(dados, produto);
        return produtoRepository.save(produto);
    }

    @Transactional
    public void definirPrecoVenda(Long id, BigDecimal novoPreco) {
        Produto produto = buscarPorId(id);
        produto.setPrecoVenda(novoPreco);
        produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void atualizarUrlImagem(Long id, String url) {
        Produto produto = buscarPorId(id);
        produto.setUrlImagem(url);
        produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void inativarPorEan(String ean) {
        var produto = produtoRepository.findByCodigoBarras(ean).orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void reativarPorEan(String ean) {
        var produto = produtoRepository.findByEanIrrestrito(ean).orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setAtivo(true);
        produtoRepository.save(produto);
    }

    @Transactional
    public ResponseEntity<Map<String, Object>> realizarSaneamentoFiscal() {
        List<Produto> produtos = produtoRepository.findAll();
        int atualizados = 0;
        for (Produto p : produtos) {
            if (calculadoraFiscalService.aplicarRegrasFiscais(p)) {
                produtoRepository.save(p);
                atualizados++;
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Saneamento concluído!");
        response.put("produtosCorrigidos", atualizados);
        return ResponseEntity.ok(response);
    }

    private void copiarDtoParaEntidade(ProdutoDTO dto, Produto produto) {
        produto.setCodigoBarras(dto.codigoBarras());
        produto.setDescricao(dto.descricao());
        produto.setMarca(dto.marca());
        produto.setCategoria(dto.categoria());
        produto.setSubcategoria(dto.subcategoria());
        produto.setUnidade(dto.unidade() != null ? dto.unidade() : "UN");
        produto.setNcm(dto.ncm());
        produto.setCest(dto.cest());
        produto.setCst(dto.cst());
        produto.setOrigem(dto.origem() != null ? dto.origem() : "0");
        produto.setMonofasico(Boolean.TRUE.equals(dto.monofasico()));
        produto.setClassificacaoReforma(dto.classificacaoReforma() != null ? dto.classificacaoReforma() : TipoTributacaoReforma.PADRAO);
        produto.setImpostoSeletivo(Boolean.TRUE.equals(dto.impostoSeletivo()));
        produto.setPrecoCusto(dto.precoCusto());
        produto.setPrecoVenda(dto.precoVenda());
        produto.setEstoqueMinimo(dto.estoqueMinimo());
        produto.setDiasParaReposicao(dto.diasParaReposicao());
        produto.setUrlImagem(dto.urlImagem());
    }
}