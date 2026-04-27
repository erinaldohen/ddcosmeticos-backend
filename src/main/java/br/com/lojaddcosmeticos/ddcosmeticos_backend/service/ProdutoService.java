package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ItemVendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao.CosmosService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProdutoService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CalculadoraFiscalService calculadoraFiscalService;
    @Autowired private AuditoriaService auditoriaService;
    @Autowired private CosmosService cosmosService;
    @Autowired private ItemVendaRepository itemVendaRepository;
    @Autowired
    private MotorVisaoComputacionalService motorVisaoService;

    public List<Map<String, String>> buscarNcmsInteligente(String termo) {
        List<Map<String, String>> resultados = new ArrayList<>();
        try {
            String ncmSugerido = produtoRepository.findNcmInteligente(termo);
            if (ncmSugerido != null && !ncmSugerido.isBlank() && !ncmSugerido.equals("00000000")) {
                Map<String, String> sugestao = new HashMap<>();
                sugestao.put("codigo", ncmSugerido);
                sugestao.put("descricao", "⭐ Sugestão Baseada no seu Histórico");
                resultados.add(sugestao);
            }
            if (termo.matches("\\d+")) {
                Map<String, String> digitado = new HashMap<>();
                digitado.put("codigo", termo);
                digitado.put("descricao", "Utilizar código digitado");
                if (resultados.isEmpty() || !resultados.get(0).get("codigo").equals(termo)) {
                    resultados.add(digitado);
                }
            }
        } catch (Exception e) {
            log.error("Erro na busca inteligente de NCM: ", e);
        }
        return resultados;
    }

    public Map<String, String> analisarProdutoComIA(Map<String, String> payload) {
        String descricao = payload.getOrDefault("descricao", "");
        String codigoBarras = payload.getOrDefault("codigoBarras", "");

        log.info("🤖 Iniciando análise de IA para o produto: {}", descricao);

        Map<String, String> respostaIA = new HashMap<>();
        String descUpper = descricao.toUpperCase();

        if (descUpper.contains("SHAMPOO") || descUpper.contains("CONDICIONADOR") || descUpper.contains("MASCARA CAPILAR")) {
            respostaIA.put("categoria", "CABELO");
            respostaIA.put("subcategoria", "TRATAMENTO CAPILAR");
            respostaIA.put("ncm", "33059000");
        } else if (descUpper.contains("BATOM") || descUpper.contains("BASE") || descUpper.contains("RIMEL")) {
            respostaIA.put("categoria", "MAQUIAGEM");
            respostaIA.put("subcategoria", descUpper.contains("BATOM") ? "LABIOS" : "ROSTO");
            respostaIA.put("ncm", "33042010");
        } else if (descUpper.contains("PERFUME") || descUpper.contains("DEO COLONIA")) {
            respostaIA.put("categoria", "PERFUMARIA");
            respostaIA.put("subcategoria", "FRAGRANCIAS");
            respostaIA.put("ncm", "33030010");
        } else if (descUpper.contains("ESMALTE")) {
            respostaIA.put("categoria", "UNHAS");

            respostaIA.put("subcategoria", "ESMALTES");
            respostaIA.put("ncm", "33043000");
        } else {
            respostaIA.put("categoria", "PELE");
            respostaIA.put("subcategoria", "CUIDADOS DIARIOS");
            respostaIA.put("ncm", "33049990");
        }

        respostaIA.put("marca", payload.getOrDefault("marca", ""));
        return respostaIA;
    }

    public Map<String, Object> importarProdutos(MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        if (file.isEmpty()) { response.put("sucesso", false); response.put("mensagem", "Arquivo vazio."); return response; }
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && (originalFilename.toLowerCase().endsWith(".xlsx") || originalFilename.toLowerCase().endsWith(".xls"))) {
                return processarExcelBruto(file);
            } else { return processarCsvBruto(file.getBytes()); }
        } catch (Exception e) {
            log.error("Erro interno na importação: ", e);
            response.put("sucesso", false); response.put("mensagem", "Erro interno: " + e.getMessage()); return response;
        }
    }

    private Map<String, Integer> criarMapaColunasInteligente(String[] headers) {
        Map<String, Integer> mapa = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String h = normalizarTexto(headers[i]);
            if (h.contains("EAN") || h.contains("BARRAS") || h.contains("GTIN") || h.equals("CODIGO")) mapa.put("ean", i);
            else if (h.contains("CUSTO") || h.contains("COMPRA")) mapa.put("custo", i);
            else if (h.contains("VENDA") || h.contains("PRECO")) mapa.put("venda", i);
            else if (h.contains("MIN") || h.contains("ALERTA")) mapa.put("min", i);
            else if (h.contains("FISCAL") && !h.contains("NAO")) mapa.put("fiscal", i);
            else if (h.contains("NAOFISCAL")) mapa.put("naofiscal", i);
            else if (h.contains("ESTOQUE") || h.contains("QTD")) mapa.put("qtd", i);
            else if (h.contains("CST")) mapa.put("cst", i);
            else if (h.contains("ORIGEM")) mapa.put("origem", i);
            else if (h.contains("NCM")) mapa.put("ncm", i);
            else if (h.contains("CEST")) mapa.put("cest", i);
            else if (h.contains("SUBCATEGORIA")) mapa.put("sub", i);
            else if (h.contains("CATEGORIA")) mapa.put("cat", i);
            else if (h.contains("MARCA")) mapa.put("marca", i);
            else if (h.contains("UNIDADE")) mapa.put("unidade", i);
            else if (h.contains("ATIVO")) mapa.put("ativo", i);
            else if (!mapa.containsKey("desc") && (h.contains("DESC") || h.contains("NOME") || h.equals("PRODUTO") || h.equals("NOMEDOPRODUTO") || h.contains("ARTIGO") || h.contains("ITEM")) && !h.contains("CATEGORIA") && !h.contains("SUBCATEGORIA")) { mapa.put("desc", i); }
        }
        return mapa;
    }

    private String getVal(String[] dados, Integer idx) { return (idx == null || idx < 0 || idx >= dados.length) ? "" : dados[idx].replace("\"", "").trim(); }

    private BigDecimal lerDecimal(String val) {
        if (val == null || val.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            val = val.replace("R$", "").trim();
            if (val.contains(",") && val.contains(".")) { val = val.replace(".", "").replace(",", "."); }
            else if (val.contains(",")) { val = val.replace(",", "."); }
            return new BigDecimal(val);
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String truncar(String str, int max) { return (str == null || str.length() <= max) ? (str == null ? "" : str) : str.substring(0, max); }
    private String normalizarTexto(String str) { return str == null ? "" : Normalizer.normalize(str.replace("\uFEFF", "").toUpperCase(), Normalizer.Form.NFD).replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").replaceAll("[^A-Z0-9]", ""); }

    @Transactional(readOnly = true)
    public Page<ProdutoListagemDTO> listarResumo(
            String termo, String marca, String categoria, String statusEstoque,
            Boolean semImagem, Boolean semNcm, Boolean precoZero, Boolean revisaoPendente, Pageable pageable
    ) {
        if (pageable == null) pageable = Pageable.unpaged();
        if (termo != null && termo.trim().isEmpty()) termo = null;
        if (marca != null && marca.trim().isEmpty()) marca = null;
        if (categoria != null && categoria.trim().isEmpty()) categoria = null;
        if (statusEstoque != null && (statusEstoque.trim().isEmpty() || "todos".equalsIgnoreCase(statusEstoque))) statusEstoque = null;
        if (semImagem == null) semImagem = false;
        if (semNcm == null) semNcm = false;
        if (precoZero == null) precoZero = false;
        if (revisaoPendente == null) revisaoPendente = false;

        return produtoRepository.buscarComFiltros(
                termo, marca, categoria, statusEstoque, semImagem, semNcm, precoZero, revisaoPendente, pageable
        ).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProdutoListagemDTO> listarResumo(String termo, Pageable pageable) {
        return listarResumo(termo, null, null, null, false, false, false, false, pageable);
    }

    private ProdutoListagemDTO toDTO(Produto p) {
        return new ProdutoListagemDTO(
                p.getId(), p.getDescricao(), p.getPrecoVenda(), p.getUrlImagem(),
                p.getQuantidadeEmEstoque(), p.isAtivo(), p.getCodigoBarras(), p.getMarca(), p.getNcm()
        );
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) { return produtoRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Não encontrado")); }

    @Transactional(readOnly = true)
    public List<Produto> buscarLixeira() { return produtoRepository.findAllLixeira(); }

    @Transactional(readOnly = true)
    public List<Produto> listarBaixoEstoque() { return produtoRepository.findProdutosComBaixoEstoque(); }

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistorico(Long id) { return auditoriaService.buscarHistoricoDoProduto(id); }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoDTO salvar(ProdutoDTO dto) {
        Produto produto = new Produto();
        atualizarDados(produto, dto);
        // 🔥 GATILHO DA BARREIRA FISCAL AQUI: Criação unitária de produto
        produto.setCodigoBarras(auditarECorrigirEanGs1(produto.getCodigoBarras()));
        Produto salvo = salvar(produto);
        return new ProdutoDTO(salvo);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Produto salvar(Produto produto) {
        // 🔥 Dupla Checagem na Raiz
        produto.setCodigoBarras(auditarECorrigirEanGs1(produto.getCodigoBarras()));
        calculadoraFiscalService.aplicarRegrasFiscais(produto);
        return produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Produto atualizar(Long id, ProdutoDTO dto) {
        Produto produto = buscarPorId(id);
        atualizarDados(produto, dto);
        return salvar(produto);
    }

    private void atualizarDados(Produto p, ProdutoDTO d) {
        p.setDescricao(d.descricao());
        p.setCodigoBarras(d.codigoBarras());
        p.setSku(d.sku());
        p.setLote(d.lote());
        p.setValidade(d.validade());
        p.setMarca(d.marca());
        p.setCategoria(d.categoria());
        p.setSubcategoria(d.subcategoria());
        p.setUnidade(d.unidade());
        p.setNcm(d.ncm());
        p.setCest(d.cest());
        p.setCst(d.cst());
        p.setOrigem(d.origem());
        p.setIsMonofasico(d.monofasico() != null ? d.monofasico() : false);
        p.setClassificacaoReforma(d.classificacaoReforma());
        p.setIsImpostoSeletivo(d.impostoSeletivo() != null ? d.impostoSeletivo() : false);
        p.setPrecoVenda(d.precoVenda() != null ? d.precoVenda() : BigDecimal.ZERO);
        if (d.precoCusto() != null) { p.setPrecoCusto(d.precoCusto()); } else if (p.getPrecoCusto() == null) { p.setPrecoCusto(BigDecimal.ZERO); }
        p.setEstoqueMinimo(d.estoqueMinimo());
        p.setDiasParaReposicao(d.diasParaReposicao());
        p.setUrlImagem(d.urlImagem());
        p.setAtivo(d.ativo());
        if (d.revisaoPendente() != null) { p.setRevisaoPendente(d.revisaoPendente()); }
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void definirPrecoVenda(Long id, BigDecimal p) { Produto prod = buscarPorId(id); prod.setPrecoVenda(p); produtoRepository.save(prod); }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void definirPrecoCusto(Long id, BigDecimal custo) { Produto prod = buscarPorId(id); prod.setPrecoCusto(custo); produtoRepository.save(prod); }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void inativarPorEan(String ean) {
        if (ean == null) throw new IllegalArgumentException("EAN invalido");
        Produto produto = produtoRepository.findByCodigoBarras(ean.trim()).orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + ean.trim()));
        produto.setAtivo(false); produtoRepository.saveAndFlush(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void reativarPorEan(String ean) { produtoRepository.findByEanIrrestrito(ean).ifPresent(p -> { p.setAtivo(true); produtoRepository.save(p); }); }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Map<String, Object> saneamentoFiscal() {
        List<Produto> todos = produtoRepository.findAll(); int alterados = 0; List<Produto> paraSalvar = new ArrayList<>();
        for (Produto p : todos) {
            boolean mudou = calculadoraFiscalService.aplicarRegrasFiscais(p);
            if (p.getNcm() != null && p.getNcm().startsWith("3401") && p.getClassificacaoReforma() != TipoTributacaoReforma.REDUZIDA_60) {
                p.setClassificacaoReforma(TipoTributacaoReforma.REDUZIDA_60); mudou = true;
            }
            if (mudou) { paraSalvar.add(p); alterados++; }
        }
        if (!paraSalvar.isEmpty()) produtoRepository.saveAll(paraSalvar);
        Map<String, Object> resultado = new HashMap<>(); resultado.put("sucesso", true); resultado.put("totalAtualizado", alterados); return resultado;
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Map<String, Object> saneamentoCustos() {
        List<Produto> todos = produtoRepository.findAll(); int alterados = 0;
        for (Produto p : todos) {
            if (p.getPrecoCusto() == null || p.getPrecoCusto().compareTo(BigDecimal.ZERO) == 0) {
                if (p.getPrecoVenda() != null && p.getPrecoVenda().compareTo(BigDecimal.ZERO) > 0) {
                    p.setPrecoCusto(p.getPrecoVenda().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP)); alterados++;
                }
            }
        }
        if (alterados > 0) { produtoRepository.saveAll(todos); }
        Map<String, Object> resultado = new HashMap<>(); resultado.put("sucesso", true); resultado.put("totalAtualizado", alterados); resultado.put("mensagem", "Custos zerados foram preenchidos com 50% do valor de venda."); return resultado;
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Map<String, Object> corrigirNcmsEmMassa() {
        List<Produto> todos = produtoRepository.findAll();
        int corrigidos = 0;

        for (Produto p : todos) {
            String ncmAtual = p.getNcm() == null ? "" : p.getNcm().replaceAll("\\D", "");
            String desc = p.getDescricao() != null ? p.getDescricao().toUpperCase() : "";

            boolean ncmAnomalia = ncmAtual.isEmpty() ||
                    ncmAtual.equals("00000000") ||
                    (!ncmAtual.startsWith("33") && !ncmAtual.startsWith("34"));

            String ncmSugerido = ncmAtual;

            if (desc.contains("HENNA") || desc.contains("SOBRANCELHA") || desc.contains("MAKE") || desc.contains("BASE ") || desc.contains("PO COMPACTO") || desc.contains("CORRETIVO") || desc.contains("PRIMER") || desc.contains("SERUM")) {
                ncmSugerido = "33049990";
            } else if (desc.contains("SHAMPOO")) {
                ncmSugerido = "33051000";
            } else if (desc.contains("CONDICIONADOR") || desc.contains("MASCARA") || desc.contains("ATIVADOR") || desc.contains("CREME CAPILAR") || desc.contains("GELATINA") || desc.contains("LEAVE-IN") || desc.contains("REPARADOR")) {
                ncmSugerido = "33059000";
            } else if (desc.contains("BATOM") || desc.contains("GLOSS") || desc.contains("LIP")) {
                ncmSugerido = "33041000";
            } else if (desc.contains("RIMEL") || desc.contains("MASCARA DE CILIOS") || desc.contains("DELINEADOR") || desc.contains("LAPIS DE OLHO")) {
                ncmSugerido = "33042010";
            } else if (desc.contains("ESMALTE") || desc.contains("ACETONA") || desc.contains("REMOVEDOR")) {
                ncmSugerido = "33043000";
            } else if (desc.contains("PERFUME") || desc.contains("COLONIA") || desc.contains("FRAGRANCIA")) {
                ncmSugerido = "33030010";
            } else if (desc.contains("DESODORANTE") || desc.contains("ANTITRANSPIRANTE")) {
                ncmSugerido = "33072010";
            } else if (desc.contains("SABONETE")) {
                ncmSugerido = "34011190";
            }

            if (!ncmSugerido.equals(ncmAtual) || ncmAnomalia) {

                if (ncmSugerido.equals(ncmAtual) && ncmAnomalia) {
                    try {
                        String ncmBanco = produtoRepository.findNcmInteligente(desc.split(" ")[0]);
                        if (ncmBanco != null && !ncmBanco.isEmpty()) {
                            ncmSugerido = ncmBanco;
                        }
                    } catch (Exception ignored) {}
                }

                if (!ncmSugerido.equals(ncmAtual) && !ncmSugerido.isEmpty()) {
                    p.setNcm(ncmSugerido);

                    boolean ehMonofasico = ncmSugerido.startsWith("3303") || ncmSugerido.startsWith("3304") ||
                            ncmSugerido.startsWith("3305") || ncmSugerido.startsWith("3307");

                    p.setIsMonofasico(ehMonofasico);
                    if (ehMonofasico) p.setCst("04");

                    produtoRepository.save(p);
                    corrigidos++;
                }
            }
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("sucesso", true);
        resultado.put("qtdCorrigidos", corrigidos);
        return resultado;
    }

    public byte[] gerarRelatorioCsv() {
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue(); StringBuilder sb = new StringBuilder();
        sb.append("ID;EAN;SKU;Descrição;Marca;Categoria;NCM;Preço Custo;Preço Venda;Estoque;Estoque Mínimo\n");
        for (Produto p : produtos) {
            sb.append(p.getId()).append(";").append(tratarCampoCsv(p.getCodigoBarras())).append(";").append(tratarCampoCsv(p.getSku())).append(";")
                    .append(tratarCampoCsv(p.getDescricao())).append(";").append(tratarCampoCsv(p.getMarca())).append(";")
                    .append(tratarCampoCsv(p.getCategoria())).append(";").append(tratarCampoCsv(p.getNcm())).append(";")
                    .append(formatarMoedaCsv(p.getPrecoCusto())).append(";").append(formatarMoedaCsv(p.getPrecoVenda())).append(";")
                    .append(p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0).append(";")
                    .append(p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    public byte[] gerarRelatorioExcel() {
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue();
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Estoque");
            CellStyle headerStyle = workbook.createCellStyle(); Font headerFont = workbook.createFont(); headerFont.setBold(true); headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] colunas = {"ID", "EAN", "SKU", "Descrição", "Marca", "Categoria", "NCM", "Preço Custo", "Preço Venda", "Estoque", "Mínimo"};
            for (int i = 0; i < colunas.length; i++) { Cell cell = headerRow.createCell(i); cell.setCellValue(colunas[i]); cell.setCellStyle(headerStyle); }

            int rowIdx = 1;
            for (Produto p : produtos) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getCodigoBarras() != null ? p.getCodigoBarras() : "");
                row.createCell(2).setCellValue(p.getSku() != null ? p.getSku() : "");
                row.createCell(3).setCellValue(p.getDescricao() != null ? p.getDescricao() : "");
                row.createCell(4).setCellValue(p.getMarca() != null ? p.getMarca() : "");
                row.createCell(5).setCellValue(p.getCategoria() != null ? p.getCategoria() : "");
                row.createCell(6).setCellValue(p.getNcm() != null ? p.getNcm() : "");
                row.createCell(7).setCellValue(p.getPrecoCusto() != null ? p.getPrecoCusto().doubleValue() : 0.0);
                row.createCell(8).setCellValue(p.getPrecoVenda() != null ? p.getPrecoVenda().doubleValue() : 0.0);
                row.createCell(9).setCellValue(p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0);
                row.createCell(10).setCellValue(p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0);
            }
            for (int i = 0; i < colunas.length; i++) { sheet.autoSizeColumn(i); } workbook.write(out); return out.toByteArray();
        } catch (IOException e) { throw new RuntimeException("Erro Excel: " + e.getMessage()); }
    }

    private String tratarCampoCsv(String valor) { return valor == null ? "" : valor.replace(";", ",").replace("\n", " ").trim(); }
    private String formatarMoedaCsv(BigDecimal valor) { return valor == null ? "0,00" : valor.toString().replace(".", ","); }

    public ProdutoDTO buscarPorEanOuExterno(String ean) {
        Optional<Produto> produtoLocal = produtoRepository.findByCodigoBarras(ean);
        if (produtoLocal.isPresent()) { return new ProdutoDTO(produtoLocal.get()); }
        try {
            Optional<ProdutoExternoDTO> dadosExternos = cosmosService.consultarEan(ean);
            if (dadosExternos.isPresent()) {
                ProdutoExternoDTO ext = dadosExternos.get();
                return new ProdutoDTO(
                        null, ext.getNome(), ext.getEan(), null, ext.getMarca(), ext.getCategoria(), null, "UN", null, null, ext.getNcm(), ext.getCest(), "102", "0", ext.getMonofasico(), TipoTributacaoReforma.PADRAO, false,
                        BigDecimal.valueOf(ext.getPrecoMedio() != null ? ext.getPrecoMedio() : 0.0), BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, 5, 0, ext.getUrlImagem(), true, false
                );
            }
        } catch (Exception e) { log.error("Erro ao consultar API externa: {}", e.getMessage()); }
        return null;
    }

    public String gerarProximoEanInterno() {
        Long maxId = produtoRepository.findMaxId(); if (maxId == null) maxId = 0L; return String.format("2%012d", maxId + 1);
    }

    public List<Produto> listarTodosAtivos() { return produtoRepository.findAllByAtivoTrue(); }

    public List<SugestaoCompraDTO> gerarSugestaoCompra() {
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue(); List<SugestaoCompraDTO> sugestoes = new ArrayList<>();
        for (Produto produto : produtos) {
            if (produto.getQuantidadeEmEstoque() <= produto.getEstoqueMinimo()) {
                int quantidadeFaltante = produto.getEstoqueMinimo() - produto.getQuantidadeEmEstoque();
                int sugestaoCompra = Math.max(quantidadeFaltante + 10, 10);
                BigDecimal custoEstimado = produto.getPrecoCusto() != null ? produto.getPrecoCusto().multiply(new BigDecimal(sugestaoCompra)) : BigDecimal.ZERO;
                String urgencia = "NORMAL";
                if (produto.getQuantidadeEmEstoque() == 0) urgencia = "CRÍTICO (ZERADO)"; else if (produto.getQuantidadeEmEstoque() < produto.getEstoqueMinimo() / 2) urgencia = "ALTA";
                sugestoes.add(new SugestaoCompraDTO(produto.getCodigoBarras(), produto.getDescricao(), produto.getMarca(), produto.getQuantidadeEmEstoque(), produto.getEstoqueMinimo(), sugestaoCompra, urgencia, custoEstimado));
            }
        }
        sugestoes.sort((a, b) -> b.nivelUrgencia().compareTo(a.nivelUrgencia())); return sugestoes;
    }

    public List<Produto> buscarSugestoesCrossSell(Long produtoBaseId, int limite) {
        try {
            List<Long> idsCompradosJuntos = itemVendaRepository.descobrirProdutosMaisCompradosJuntos(produtoBaseId, limite);
            if (idsCompradosJuntos != null && !idsCompradosJuntos.isEmpty()) {
                return produtoRepository.findAllById(idsCompradosJuntos);
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar histórico de IA para cross-sell, usando fallback de regras: {}", e.getMessage());
        }
        return aplicarRegrasDeNegocio(produtoBaseId, limite);
    }

    private List<Produto> aplicarRegrasDeNegocio(Long produtoBaseId, int limite) {
        Produto produtoBase = produtoRepository.findById(produtoBaseId).orElse(null);
        if (produtoBase == null || produtoBase.getSubcategoria() == null || produtoBase.getSubcategoria().trim().isEmpty()) {
            return Collections.emptyList();
        }
        String subcatBase = produtoBase.getSubcategoria().toUpperCase().trim();
        List<String> complementosIdeais = new ArrayList<>();
        switch (subcatBase) {
            case "CÍLIOS":
            case "CILIOS":
            case "CÍLIOS POSTIÇOS":
                complementosIdeais.addAll(Arrays.asList("COLA", "COLA PARA CÍLIOS", "PINÇA", "MÁSCARA DE CÍLIOS", "ESCOVINHA"));
                break;
            case "HENNA":
            case "DESIGN DE SOBRANCELHA":
                complementosIdeais.addAll(Arrays.asList("MISTURADOR", "DAPPEN", "ALGODÃO", "REMOVEDOR", "PINÇA", "LINHA", "PAQUÍMETRO"));
                break;
            case "SHAMPOO":
                complementosIdeais.addAll(Arrays.asList("CONDICIONADOR", "MÁSCARA CAPILAR", "CREME DE PENTEAR", "SÉRUM", "ÓLEO CAPILAR"));
                break;
            case "COLORAÇÃO":
            case "TINTURA":
                complementosIdeais.addAll(Arrays.asList("ÁGUA OXIGENADA", "OX", "PÓ DESCOLORANTE", "PINCEL", "TIGELA", "LUVAS", "AMPOLA"));
                break;
            case "BASE":
            case "BASE LÍQUIDA":
            case "CORRETIVO":
                complementosIdeais.addAll(Arrays.asList("ESPONJA", "PÓ COMPACTO", "PÓ SOLTO", "BRUMA", "PRIMER", "PINCEL"));
                break;
            case "ESMALTE":
                complementosIdeais.addAll(Arrays.asList("ACETONA", "REMOVEDOR DE ESMALTE", "ALGODÃO", "LIXA", "BASE FORTALECEDORA", "EXTRA BRILHO", "PALITO"));
                break;
            case "DEPILAÇÃO":
            case "CERA":
                complementosIdeais.addAll(Arrays.asList("FOLHA PLÁSTICA", "ÓLEO REMOVEDOR", "LOÇÃO PÓS DEPILAÇÃO", "ESPÁTULA"));
                break;
            case "MAQUIAGEM":
                complementosIdeais.addAll(Arrays.asList("DEMAQUILANTE", "ÁGUA MICELAR", "LENÇO UMEDECIDO"));
                break;
            default:
                break;
        }

        Pageable limitPage = PageRequest.of(0, limite);
        if (!complementosIdeais.isEmpty()) {
            List<Produto> sugestoes = produtoRepository.findComplementares(complementosIdeais, produtoBaseId, limitPage).getContent();
            if (!sugestoes.isEmpty()) { return sugestoes; }
        }
        if (produtoBase.getCategoria() != null && !produtoBase.getCategoria().trim().isEmpty()) {
            return produtoRepository.findByCategoriaAndSubcategoriaNotAndIdNotAndAtivoTrue(
                    produtoBase.getCategoria(), produtoBase.getSubcategoria(), produtoBaseId, limitPage).getContent();
        }
        return Collections.emptyList();
    }

    // =========================================================================
    // 🔥 MOTOR DE SANEAMENTO: CORREÇÃO DE EAN INTERNO (GS1 - MÓDULO 10) 🔥
    // =========================================================================
    @Transactional
    public java.util.Map<String, Object> corrigirEansInternosIa() {
        // Query ultra-rápida (Evita Full Table Scan)
        List<Produto> produtos = produtoRepository.findProdutosComEanInterno();
        int corrigidos = 0;
        int conflitosIgnorados = 0;

        for (Produto produto : produtos) {
            String ean = produto.getCodigoBarras();
            String eanCorreto = auditarECorrigirEanGs1(ean);

            if (!ean.equals(eanCorreto)) {
                Optional<Produto> produtoExistente = produtoRepository.findByCodigoBarras(eanCorreto);

                if (produtoExistente.isEmpty() || produtoExistente.get().getId().equals(produto.getId())) {
                    try {
                        produto.setCodigoBarras(eanCorreto);
                        produtoRepository.saveAndFlush(produto);
                        corrigidos++;
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        System.out.println("⚠️ Robô EAN: Conflito capturado ao salvar EAN " + eanCorreto + ". Ignorando.");
                        conflitosIgnorados++;
                    }
                } else {
                    conflitosIgnorados++;
                }
            }
        }

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("sucesso", true);
        response.put("qtdCorrigidos", corrigidos);
        response.put("qtdConflitos", conflitosIgnorados);

        if (conflitosIgnorados > 0) {
            response.put("mensagem", "Saneamento concluído: " + corrigidos + " corrigidos. " + conflitosIgnorados + " ignorados por já existirem no banco.");
        } else {
            response.put("mensagem", "Saneamento de EANs internos concluído com " + corrigidos + " correções.");
        }

        return response;
    }

    /**
     * 🔥 BARREIRA DE ENTRADA: Corrige EANs internos on-the-fly durante importações e criações.
     */
    public String auditarECorrigirEanGs1(String eanRaw) {
        if (eanRaw != null && eanRaw.startsWith("2") && eanRaw.length() == 13) {
            String base = eanRaw.substring(0, 12);
            int soma = 0;
            for (int i = 0; i < 12; i++) {
                int digito = Character.getNumericValue(base.charAt(i));
                soma += (i % 2 == 0) ? digito : (digito * 3);
            }
            int digitoVerificadorEsperado = (10 - (soma % 10)) % 10;
            return base + digitoVerificadorEsperado;
        }
        return eanRaw;
    }

    // =========================================================================
    // 🔥 GERADOR DE ETIQUETAS TÉRMICAS (ZPL) 🔥
    // =========================================================================
    @Transactional(readOnly = true)
    public String imprimirEtiqueta(Long id) {
        return gerarZplProduto(buscarPorId(id));
    }

    // Helper isolado para gerar o código Zebra
    private String gerarZplProduto(Produto produto) {
        String ean = produto.getCodigoBarras() != null ? produto.getCodigoBarras() : "0000000000000";
        String descricao = produto.getDescricao() != null ? produto.getDescricao() : "PRODUTO SEM NOME";
        if (descricao.length() > 25) descricao = descricao.substring(0, 25);
        String precoStr = produto.getPrecoVenda() != null ? String.format(java.util.Locale.of("pt", "BR"), "R$ %.2f", produto.getPrecoVenda().doubleValue()) : "R$ 0,00";

        StringBuilder zpl = new StringBuilder();
        zpl.append("^XA\n^PW400\n^CF0,30\n^FO20,20^FDDD COSMETICOS^FS\n^FO20,55^GB360,2,2^FS\n^CF0,25\n^FO20,70^FD")
                .append(descricao).append("^FS\n^BY2,2,50\n^FO20,110^BEN,50,Y,N^FD").append(ean)
                .append("^FS\n^CF0,40\n^FO220,180^FD").append(precoStr).append("^FS\n^XZ");
        return zpl.toString();
    }

    // =========================================================================
    // 🔥 IMPORTAÇÃO TRATOR (FAIL-SAFE): NENHUM PRODUTO FICA PARA TRÁS
    // =========================================================================

    private String gerarEanFallbackLote(java.util.concurrent.atomic.AtomicLong counter) {
        // Pega o próximo número disponível e formata como EAN base de 12 dígitos
        String base = String.format("2%011d", counter.incrementAndGet());
        // Passa pelo robô matemático para gerar o 13º dígito verificador perfeito
        return auditarECorrigirEanGs1(base + "0");
    }

    private Map<String, Object> processarExcelBruto(MultipartFile file) {
        List<Produto> lote = new ArrayList<>();
        List<String> listaAvisos = new ArrayList<>();
        Map<String, Object> resultado = new HashMap<>();

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) { resultado.put("sucesso", false); resultado.put("mensagem", "Arquivo vazio."); return resultado; }

            Row headerRow = sheet.getRow(0); int numCols = headerRow.getLastCellNum(); String[] headers = new String[numCols];
            DataFormatter dataFormatter = new DataFormatter();
            for (int i = 0; i < numCols; i++) { headers[i] = dataFormatter.formatCellValue(headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim(); }

            Map<String, Integer> mapa = criarMapaColunasInteligente(headers);
            if (!mapa.containsKey("ean")) { resultado.put("sucesso", false); resultado.put("mensagem", "ERRO: Coluna EAN não encontrada."); return resultado; }

            Map<String, Produto> produtosNoLote = new HashMap<>();

            // Contador atômico para gerar EANs internos sem repetir, começando do ID máximo do banco
            Long maxIdDb = produtoRepository.findMaxId();
            java.util.concurrent.atomic.AtomicLong eanCounter = new java.util.concurrent.atomic.AtomicLong(maxIdDb == null ? 0L : maxIdDb);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i); if (row == null) continue;
                String[] dados = new String[numCols]; boolean rowIsEmpty = true;
                for (int j = 0; j < numCols; j++) { dados[j] = dataFormatter.formatCellValue(row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim(); if (!dados[j].isEmpty()) rowIsEmpty = false; }
                if (rowIsEmpty) continue;

                try {
                    String eanBruto = getVal(dados, mapa.get("ean"));
                    String eanRaw = eanBruto.replaceAll("[^0-9]", "");
                    boolean precisouGerarEan = false;
                    String eanCorrigido;

                    // 1. ANÁLISE DE EAN: Vazio ou Notação Científica? Gera um novo.
                    if (eanRaw.isEmpty() || eanBruto.toUpperCase().contains("E+")) {
                        eanCorrigido = gerarEanFallbackLote(eanCounter);
                        precisouGerarEan = true;
                        listaAvisos.add("Linha " + (i+1) + ": EAN ausente. Gerado código interno (" + eanCorrigido + ").");
                    } else {
                        // Limpa e calcula o dígito GS1
                        eanCorrigido = auditarECorrigirEanGs1(eanRaw);

                        // 2. ANÁLISE DE COLISÃO: Já existe no lote? Gera um novo.
                        if (produtosNoLote.containsKey(eanCorrigido)) {
                            eanCorrigido = gerarEanFallbackLote(eanCounter);
                            precisouGerarEan = true;
                            listaAvisos.add("Linha " + (i+1) + ": Colisão de EAN resolvida. Gerado código interno (" + eanCorrigido + ").");
                        }
                    }

                    // Cria o produto forçando a entrada
                    Produto p = criarProdutoDaLinha(dados, mapa, eanCorrigido, precisouGerarEan);
                    produtosNoLote.put(eanCorrigido, p);
                    lote.add(p);

                } catch (Exception e) {
                    listaAvisos.add("Linha " + (i+1) + ": Erro Inesperado (" + e.getMessage() + ")");
                }
            }

            if (!lote.isEmpty()) {
                produtoRepository.saveAll(lote);
            }

            resultado.put("sucesso", true);
            resultado.put("qtdImportados", lote.size());
            resultado.put("qtdErros", listaAvisos.size());
            resultado.put("listaErros", listaAvisos); // Agora são apenas 'Avisos' do que a IA fez
            resultado.put("mensagem", listaAvisos.isEmpty() ? "Sucesso absoluto!" : "100% Importado. " + listaAvisos.size() + " correções de IA aplicadas.");

        } catch (Exception e) {
            resultado.put("sucesso", false); resultado.put("mensagem", "Erro crítico: " + e.getMessage());
        }
        return resultado;
    }

    private Map<String, Object> processarCsvBruto(byte[] bytes) {
        List<Produto> lote = new ArrayList<>(); List<String> listaAvisos = new ArrayList<>(); Map<String, Object> resultado = new HashMap<>();
        try {
            String conteudo = new String(bytes, StandardCharsets.UTF_8); if (conteudo.startsWith("\uFEFF")) conteudo = conteudo.substring(1);
            String[] linhas = conteudo.split("\\r?\\n");
            if (linhas.length < 2) { resultado.put("sucesso", false); resultado.put("mensagem", "Arquivo vazio."); return resultado; }
            String delimitador = linhas[0].contains(";") ? ";" : ","; String[] headers = linhas[0].split(delimitador);
            Map<String, Integer> mapa = criarMapaColunasInteligente(headers);
            if (!mapa.containsKey("ean")) { resultado.put("sucesso", false); resultado.put("mensagem", "ERRO: Coluna EAN não encontrada."); return resultado; }
            Map<String, Produto> produtosNoLote = new HashMap<>();

            Long maxIdDb = produtoRepository.findMaxId();
            java.util.concurrent.atomic.AtomicLong eanCounter = new java.util.concurrent.atomic.AtomicLong(maxIdDb == null ? 0L : maxIdDb);

            for (int i = 1; i < linhas.length; i++) {
                String linha = linhas[i].trim(); if (linha.isEmpty()) continue; String[] dados = linha.split(delimitador, -1);
                try {
                    String eanBruto = getVal(dados, mapa.get("ean"));
                    String eanRaw = eanBruto.replaceAll("[^0-9]", "");
                    boolean precisouGerarEan = false;
                    String eanCorrigido;

                    if (eanRaw.isEmpty()) {
                        eanCorrigido = gerarEanFallbackLote(eanCounter);
                        precisouGerarEan = true;
                        listaAvisos.add("Linha " + (i+1) + ": EAN ausente. Gerado interno.");
                    } else {
                        eanCorrigido = auditarECorrigirEanGs1(eanRaw);
                        if (produtosNoLote.containsKey(eanCorrigido)) {
                            eanCorrigido = gerarEanFallbackLote(eanCounter);
                            precisouGerarEan = true;
                            listaAvisos.add("Linha " + (i+1) + ": Colisão de EAN resolvida.");
                        }
                    }

                    Produto p = criarProdutoDaLinha(dados, mapa, eanCorrigido, precisouGerarEan);
                    produtosNoLote.put(eanCorrigido, p); lote.add(p);
                } catch (Exception e) { listaAvisos.add("Linha " + (i+1) + ": Erro Crítico."); }
            }
            if (!lote.isEmpty()) { produtoRepository.saveAll(lote); }
            resultado.put("sucesso", true); resultado.put("qtdImportados", lote.size()); resultado.put("qtdErros", listaAvisos.size()); resultado.put("listaErros", listaAvisos);
        } catch (Exception e) { resultado.put("sucesso", false); resultado.put("mensagem", "Erro crítico."); }
        return resultado;
    }

    private Produto criarProdutoDaLinha(String[] dados, Map<String, Integer> mapa, String eanCorrigido, boolean precisouGerarEan) {
        Produto p = produtoRepository.findByEanIrrestrito(eanCorrigido).stream().findFirst().orElse(new Produto());

        // A IA agora é confiante. Assumimos que NÃO precisa de auditoria por padrão.
        boolean precisaAuditoria = false;

        if (p.getId() == null) {
            p.setCodigoBarras(eanCorrigido); p.setSku(eanCorrigido); p.setAtivo(true);
            p.setOrigem("0"); p.setCst("102"); p.setNcm("00000000");
            p.setQuantidadeEmEstoque(0); p.setEstoqueFiscal(0); p.setEstoqueNaoFiscal(0);
        } else {
            if (mapa.containsKey("ativo")) {
                String a = getVal(dados, mapa.get("ativo")).toUpperCase();
                p.setAtivo(a.startsWith("S") || a.equals("1") || a.equals("TRUE"));
            }
        }

        if (mapa.containsKey("desc")) {
            String desc = getVal(dados, mapa.get("desc"));
            if (desc != null && !desc.trim().isEmpty()) { p.setDescricao(truncar(desc.trim().toUpperCase(), 250)); }
        }

        // 🚨 MOTIVO 1 PARA REVISÃO: Produto sem nome.
        if (p.getDescricao() == null || p.getDescricao().trim().isEmpty()) {
            p.setDescricao("PRODUTO S/ NOME " + eanCorrigido);
            precisaAuditoria = true;
        }

        if (mapa.containsKey("custo")) p.setPrecoCusto(lerDecimal(getVal(dados, mapa.get("custo"))));
        if (mapa.containsKey("venda")) p.setPrecoVenda(lerDecimal(getVal(dados, mapa.get("venda"))));
        if (mapa.containsKey("qtd")) p.setQuantidadeEmEstoque(lerDecimal(getVal(dados, mapa.get("qtd"))).intValue());

        if (p.getPrecoCusto() == null) p.setPrecoCusto(BigDecimal.ZERO);

        // 🚨 MOTIVO 2 PARA REVISÃO: Preço de venda a R$ 0,00.
        if (p.getPrecoVenda() == null || p.getPrecoVenda().compareTo(BigDecimal.ZERO) == 0) {
            if (p.getPrecoCusto().compareTo(BigDecimal.ZERO) > 0) {
                p.setPrecoVenda(p.getPrecoCusto().multiply(new BigDecimal("1.5"))); // IA aplica 50% de margem
            } else {
                precisaAuditoria = true; // Custo zero e venda zero
            }
        }

        if (mapa.containsKey("min")) {
            BigDecimal min = lerDecimal(getVal(dados, mapa.get("min")));
            if(min!=null) p.setEstoqueMinimo(min.intValue());
        }
        if (p.getEstoqueMinimo() == null) p.setEstoqueMinimo(5);
        if (mapa.containsKey("marca")) p.setMarca(truncar(getVal(dados, mapa.get("marca")).toUpperCase(), 50));
        if (mapa.containsKey("cat")) p.setCategoria(truncar(getVal(dados, mapa.get("cat")), 50));

        // NCM INTELIGENTE E FALLBACK
        String ncmArquivo = "";
        if (mapa.containsKey("ncm")) ncmArquivo = truncar(getVal(dados, mapa.get("ncm")).replaceAll("[^0-9]", ""), 8);
        boolean ncmSuspeito = ncmArquivo.isEmpty() || ncmArquivo.equals("00000000") || (ncmArquivo.startsWith("3304") && !p.getDescricao().contains("BATOM"));

        if (!ncmSuspeito) {
            p.setNcm(ncmArquivo);
        } else {
            String ncmInteligente = null;
            String[] palavras = p.getDescricao().split(" ");
            for (String palavra : palavras) {
                String pl = palavra.replaceAll("[^a-zA-Z0-9]", "");
                if (pl.length() > 3) {
                    try { ncmInteligente = produtoRepository.findNcmInteligente(pl); break; }
                    catch (Exception ignored) {}
                }
            }
            if (ncmInteligente != null) {
                p.setNcm(ncmInteligente);
            } else if (!ncmArquivo.isEmpty()) {
                p.setNcm(ncmArquivo);
            } else {
                p.setNcm("33049990"); // FALLBACK GENÉRICO COSMÉTICOS
            }
        }

        if (p.getNcm() != null && !p.getNcm().equals("00000000")) {
            try { calculadoraFiscalService.aplicarRegrasFiscais(p); }
            catch (Exception e) { /* Falha fiscal não impede venda */ }
        }

        p.setRevisaoPendente(precisaAuditoria);
        return p;
    }

    // =========================================================================
    // 🔥 DASHBOARD DE IA E QUICK FIXES (RESOLUÇÃO GUIADA)
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> obterRaioXInteligenciaArtificial() {
        List<Produto> todos = produtoRepository.findAllByAtivoTrue();

        int semCusto = 0, semNcm = 0, ncmInvalido = 0, semDescricao = 0, semMarca = 0, precoVendaZerado = 0, divergenciaGondola = 0;

        for (Produto p : todos) {
            if (p.getPrecoCusto() == null || p.getPrecoCusto().compareTo(BigDecimal.ZERO) == 0) semCusto++;
            if (p.getPrecoVenda() == null || p.getPrecoVenda().compareTo(BigDecimal.ZERO) == 0) precoVendaZerado++;
            if (p.getNcm() == null || p.getNcm().isEmpty() || p.getNcm().equals("00000000")) semNcm++;
            else if (p.getNcm().length() != 8) ncmInvalido++;
            if (p.getDescricao() == null || p.getDescricao().trim().isEmpty() || p.getDescricao().contains("PRODUTO S/ NOME")) semDescricao++;
            if (p.getMarca() == null || p.getMarca().trim().isEmpty()) semMarca++;

            // Conta EXATAMENTE as divergências do corredor
            if (Boolean.TRUE.equals(p.getAlertaGondola())) {
                divergenciaGondola++;
            }
        }

        int totalAnomalias = semCusto + semNcm + ncmInvalido + semDescricao + semMarca + precoVendaZerado + divergenciaGondola;

        Map<String, Object> relatorio = new HashMap<>();
        relatorio.put("totalAnomalias", totalAnomalias);
        relatorio.put("semCusto", semCusto);
        relatorio.put("precoVendaZerado", precoVendaZerado);
        relatorio.put("semNcm", semNcm);
        relatorio.put("ncmInvalido", ncmInvalido);
        relatorio.put("semDescricao", semDescricao);
        relatorio.put("semMarca", semMarca);
        relatorio.put("divergenciaGondola", divergenciaGondola);

        return relatorio;
    }

    @Transactional
    public Map<String, Object> aplicarQuickFixIA(String tipoAnomalia) {
        List<Produto> todos = produtoRepository.findAllByAtivoTrue();
        int corrigidos = 0;
        StringBuilder zplLote = new StringBuilder(); // Vai guardar todas as etiquetas geradas de uma vez

        for (Produto p : todos) {
            boolean salvou = false;
            switch (tipoAnomalia.toUpperCase()) {
                case "SEM_CUSTO":
                    if (p.getPrecoCusto() == null || p.getPrecoCusto().compareTo(BigDecimal.ZERO) == 0) {
                        if (p.getPrecoVenda() != null && p.getPrecoVenda().compareTo(BigDecimal.ZERO) > 0) {
                            p.setPrecoCusto(p.getPrecoVenda().divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP)); salvou = true;
                        }
                    }
                    break;
                case "PRECO_VENDA_ZERADO":
                    if (p.getPrecoVenda() == null || p.getPrecoVenda().compareTo(BigDecimal.ZERO) == 0) {
                        if (p.getPrecoCusto() != null && p.getPrecoCusto().compareTo(BigDecimal.ZERO) > 0) {
                            p.setPrecoVenda(p.getPrecoCusto().multiply(new BigDecimal("1.5"))); salvou = true;
                        }
                    }
                    break;
                case "SEM_NCM":
                case "NCM_INVALIDO":
                    String ncm = p.getNcm();
                    if (ncm == null || ncm.isEmpty() || ncm.equals("00000000") || ncm.length() != 8) {
                        p.setNcm("33049990"); p.setIsMonofasico(true); p.setCst("04"); salvou = true;
                    }
                    break;
                case "SEM_MARCA":
                    if (p.getMarca() == null || p.getMarca().trim().isEmpty()) { p.setMarca("DIVERSOS"); salvou = true; }
                    break;
                case "DIVERGENCIA":
                    // 🔥 A SOLUÇÃO MÁGICA DA GÔNDOLA
                    if (Boolean.TRUE.equals(p.getRevisaoPendente())) {
                        boolean erroCadastro = (p.getPrecoCusto() == null || p.getPrecoCusto().compareTo(BigDecimal.ZERO) == 0) ||
                                (p.getPrecoVenda() == null || p.getPrecoVenda().compareTo(BigDecimal.ZERO) == 0) ||
                                (p.getNcm() == null || p.getNcm().isEmpty() || p.getNcm().equals("00000000") || p.getNcm().length() != 8) ||
                                (p.getDescricao() == null || p.getDescricao().trim().isEmpty() || p.getDescricao().contains("PRODUTO S/ NOME"));

                        if (!erroCadastro) {
                            // É uma divergência limpa! Limpamos o alerta e geramos a etiqueta.
                            p.setRevisaoPendente(false);
                            salvou = true;
                            zplLote.append(gerarZplProduto(p)).append("\n"); // Adiciona à bobina de impressão
                        }
                    }
                    break;
            }

            if (salvou) {
                p.setRevisaoPendente(false); produtoRepository.save(p); corrigidos++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sucesso", true);
        response.put("qtdCorrigidos", corrigidos);
        // Se gerámos etiquetas, enviamos para o React imprimir!
        if (zplLote.length() > 0) { response.put("zpl", zplLote.toString()); }

        return response;
    }

    @Transactional
    public void sinalizarDivergenciaGondola(Long id) {
        Produto p = buscarPorId(id);
        p.setAlertaGondola(true); // Flag exclusiva e cirúrgica
        produtoRepository.save(p);
    }
    // =========================================================================
    // 🔥 RESOLUÇÃO ESPECÍFICA DE DIVERGÊNCIA DE GÔNDOLA
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ProdutoListagemDTO> listarDivergenciasGondola() {
        return produtoRepository.findAllByAtivoTrue().stream()
                .filter(p -> Boolean.TRUE.equals(p.getAlertaGondola())) // Apenas alertas reais
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, String> resolverDivergenciaEImprimir(Long id, BigDecimal novoPrecoVenda) {
        Produto p = buscarPorId(id);
        p.setPrecoVenda(novoPrecoVenda);
        p.setAlertaGondola(false); // Desliga o alerta após corrigir

        // Também desliga a revisão genérica caso exista, pois o gerente já conferiu o produto
        p.setRevisaoPendente(false);

        produtoRepository.save(p);

        Map<String, String> response = new HashMap<>();
        response.put("zpl", gerarZplProduto(p)); // Etiqueta pronta
        return response;
    }
    // =========================================================================
    // 🔥 EDIÇÃO INLINE (MODO EXCEL)
    // =========================================================================
    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void ajustarEstoqueRapido(Long id, Integer quantidade) {
        Produto prod = buscarPorId(id);
        prod.setQuantidadeEmEstoque(quantidade);
        produtoRepository.save(prod);
    }
}