package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Slf4j
@Service
public class ProdutoService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CalculadoraFiscalService calculadoraFiscalService;
    @Autowired private AuditoriaService auditoriaService;

    // Retorna Map para o frontend tratar erros e sucessos
    public Map<String, Object> importarProdutos(MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("sucesso", false);
            response.put("mensagem", "Arquivo vazio.");
            return response;
        }

        try {
            byte[] bytes = file.getBytes();
            Map<String, Object> relatorio = processarCsvBruto(bytes);
            return relatorio;
        } catch (Exception e) {
            e.printStackTrace();
            response.put("sucesso", false);
            response.put("mensagem", "Erro interno: " + e.getMessage());
            return response;
        }
    }

    private Map<String, Object> processarCsvBruto(byte[] bytes) {
        List<Produto> lote = new ArrayList<>();
        List<String> listaErros = new ArrayList<>();
        int salvos = 0;

        Map<String, Object> resultado = new HashMap<>();

        try {
            String conteudo = new String(bytes, StandardCharsets.UTF_8);
            if (conteudo.startsWith("\uFEFF")) conteudo = conteudo.substring(1);

            String[] linhas = conteudo.split("\\r?\\n");

            if (linhas.length < 2) {
                resultado.put("sucesso", false);
                resultado.put("mensagem", "Arquivo sem dados (apenas cabeçalho ou vazio).");
                return resultado;
            }

            // Cabeçalho e Delimitador
            String headerLine = linhas[0];
            String delimitador = headerLine.contains(";") ? ";" : ",";
            String[] headers = headerLine.split(delimitador);

            Map<String, Integer> mapa = criarMapaColunasInteligente(headers);

            if (!mapa.containsKey("ean")) {
                resultado.put("sucesso", false);
                resultado.put("mensagem", "ERRO CRÍTICO: Coluna 'EAN' ou 'Código de Barras' não encontrada.");
                return resultado;
            }

            // Processamento das Linhas
            for (int i = 1; i < linhas.length; i++) {
                String linha = linhas[i].trim();
                if (linha.isEmpty()) continue;
                String[] dados = linha.split(delimitador, -1);

                try {
                    Produto p = criarProdutoDaLinha(dados, mapa);
                    if (p != null) {
                        lote.add(p);
                    } else {
                        listaErros.add("Linha " + (i+1) + ": Ignorada (Sem EAN válido).");
                    }
                } catch (Exception e) {
                    listaErros.add("Linha " + (i+1) + ": Erro ao ler dados (" + e.getMessage() + ")");
                }
            }

            if (!lote.isEmpty()) {
                produtoRepository.saveAll(lote);
                salvos = lote.size();
            }

            // Relatório Final
            resultado.put("sucesso", true);
            resultado.put("qtdImportados", salvos);
            resultado.put("qtdErros", listaErros.size());
            resultado.put("listaErros", listaErros);

            if (listaErros.isEmpty()) {
                resultado.put("mensagem", "Sucesso! " + salvos + " produtos importados sem erros.");
            } else {
                resultado.put("mensagem", "Processado com alertas. Importados: " + salvos + ". Falhas: " + listaErros.size());
            }

        } catch (Exception e) {
            e.printStackTrace();
            resultado.put("sucesso", false);
            resultado.put("mensagem", "Erro crítico no processamento: " + e.getMessage());
        }

        return resultado;
    }

    private Map<String, Integer> criarMapaColunasInteligente(String[] headers) {
        Map<String, Integer> mapa = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String h = normalizarTexto(headers[i]);

            if (h.contains("EAN") || h.contains("BARRAS") || h.contains("GTIN") || h.equals("CODIGO")) mapa.put("ean", i);
            else if ((h.contains("DESC") || h.contains("NOME") || h.contains("PRODUTO")) && !h.contains("TIPO") && !h.contains("CATEGORIA")) mapa.put("desc", i);
            else if (h.contains("CUSTO") || h.contains("COMPRA")) mapa.put("custo", i);
            else if (h.contains("VENDA") || h.contains("VAREJO") || h.equals("PRECO")) mapa.put("venda", i);
            else if (h.contains("MIN") || h.contains("ALERTA")) mapa.put("min", i);
            else if (h.contains("FISCAL") && !h.contains("NAO")) mapa.put("fiscal", i);
            else if (h.contains("NAOFISCAL")) mapa.put("naofiscal", i);
            else if ((h.contains("ESTOQUE") || h.contains("QTD")) && !h.contains("MOVIMENTA")) mapa.put("qtd", i);
            else if (h.contains("CST")) mapa.put("cst", i);
            else if (h.contains("ORIGEM")) mapa.put("origem", i);
            else if (h.contains("NCM")) mapa.put("ncm", i);
            else if (h.contains("CEST")) mapa.put("cest", i);
            else if (h.contains("SUBCATEGORIA")) mapa.put("sub", i);
            else if (h.contains("CATEGORIA")) mapa.put("cat", i);
            else if (h.contains("MARCA")) mapa.put("marca", i);
            else if (h.contains("UNIDADE")) mapa.put("unidade", i);
            else if (h.contains("ATIVO") || h.contains("SITUACAO")) mapa.put("ativo", i);
        }
        return mapa;
    }

    private Produto criarProdutoDaLinha(String[] dados, Map<String, Integer> mapa) {
        if (!mapa.containsKey("ean")) return null;
        String ean = getVal(dados, mapa.get("ean")).replaceAll("[^0-9]", "");
        if (ean.isEmpty()) return null;

        Produto p = produtoRepository.findByCodigoBarras(ean).orElse(new Produto());

        // Defaults para novo produto
        if (p.getId() == null) {
            p.setCodigoBarras(ean);
            p.setAtivo(true);
            p.setOrigem("0"); p.setCst("102"); p.setNcm("00000000");
            p.setQuantidadeEmEstoque(0); p.setEstoqueFiscal(0); p.setEstoqueNaoFiscal(0);
        }

        // Descrição
        if (mapa.containsKey("desc")) {
            String desc = getVal(dados, mapa.get("desc")).toUpperCase();
            if (!desc.isEmpty()) p.setDescricao(truncar(desc, 250));
        }
        if (p.getDescricao() == null || p.getDescricao().trim().isEmpty()) p.setDescricao("PRODUTO " + ean);

        // Preços
        if (mapa.containsKey("custo")) p.setPrecoCusto(lerDecimal(getVal(dados, mapa.get("custo"))));
        if (mapa.containsKey("venda")) p.setPrecoVenda(lerDecimal(getVal(dados, mapa.get("venda"))));

        // Estoques
        if (mapa.containsKey("qtd")) p.setQuantidadeEmEstoque(lerDecimal(getVal(dados, mapa.get("qtd"))).intValue());
        if (mapa.containsKey("fiscal")) p.setEstoqueFiscal(lerDecimal(getVal(dados, mapa.get("fiscal"))).intValue());
        if (p.getEstoqueFiscal() == null) p.setEstoqueFiscal(0);

        if (mapa.containsKey("naofiscal")) {
            p.setEstoqueNaoFiscal(lerDecimal(getVal(dados, mapa.get("naofiscal"))).intValue());
        } else {
            int total = p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0;
            int fiscal = p.getEstoqueFiscal();
            p.setEstoqueNaoFiscal(Math.max(0, total - fiscal));
        }

        if (mapa.containsKey("min")) {
            BigDecimal min = lerDecimal(getVal(dados, mapa.get("min")));
            if(min!=null) p.setEstoqueMinimo(min.intValue());
        }
        if (p.getEstoqueMinimo() == null) p.setEstoqueMinimo(5);

        // Strings
        if (mapa.containsKey("marca")) p.setMarca(truncar(getVal(dados, mapa.get("marca")).toUpperCase(), 50));
        if (mapa.containsKey("cat")) p.setCategoria(truncar(getVal(dados, mapa.get("cat")), 50));
        if (mapa.containsKey("sub")) p.setSubcategoria(truncar(getVal(dados, mapa.get("sub")), 50));
        if (mapa.containsKey("unidade")) p.setUnidade(truncar(getVal(dados, mapa.get("unidade")), 10));

        if (mapa.containsKey("ativo")) {
            String a = getVal(dados, mapa.get("ativo")).toUpperCase();
            p.setAtivo(a.startsWith("S") || a.equals("1") || a.equals("TRUE"));
        }

        // --- NCM E INTELIGÊNCIA ARTIFICIAL (CORREÇÃO FISCAL) ---
        String ncmArquivo = "";
        if (mapa.containsKey("ncm")) {
            ncmArquivo = truncar(getVal(dados, mapa.get("ncm")).replaceAll("[^0-9]", ""), 8);
        }

        boolean ncmSuspeito = ncmArquivo.isEmpty() || ncmArquivo.equals("00000000") ||
                (ncmArquivo.startsWith("3304") && !p.getDescricao().contains("BATOM") && !p.getDescricao().contains("MAKE") && !p.getDescricao().contains("SOMBRA"));

        if (!ncmSuspeito) {
            p.setNcm(ncmArquivo);
        } else {
            String ncmInteligente = null;
            String[] palavras = p.getDescricao().split(" ");
            for (String palavra : palavras) {
                String palavraLimpa = palavra.replaceAll("[^a-zA-Z0-9]", "");
                if (palavraLimpa.length() > 3) {
                    ncmInteligente = produtoRepository.findNcmInteligente(palavraLimpa);
                    if (ncmInteligente != null) break;
                }
            }
            if (ncmInteligente != null) {
                p.setNcm(ncmInteligente);
            } else if (!ncmArquivo.isEmpty()) {
                p.setNcm(ncmArquivo);
            }
        }

        if (mapa.containsKey("cest")) p.setCest(truncar(getVal(dados, mapa.get("cest")).replaceAll("[^0-9]", ""), 7));

        if (mapa.containsKey("origem")) {
            String o = getVal(dados, mapa.get("origem")).replaceAll("[^0-9]", "");
            if(!o.isEmpty()) p.setOrigem(o.substring(0, 1));
        } else if (mapa.containsKey("cst")) {
            String c = getVal(dados, mapa.get("cst")).replaceAll("[^0-9]", "");
            if (c.length() >= 1) p.setOrigem(c.substring(0, 1));
            p.setCst(c);
        }

        // Validação Fiscal
        if (p.getNcm() != null && !p.getNcm().equals("00000000")) {
            try { calculadoraFiscalService.aplicarRegrasFiscais(p); } catch (Exception ignored) {}
        }

        // Consistência Preços
        if (p.getPrecoCusto() == null) p.setPrecoCusto(BigDecimal.ZERO);
        p.setPrecoMedioPonderado(p.getPrecoCusto());
        if (p.getPrecoVenda() == null || p.getPrecoVenda().compareTo(BigDecimal.ZERO) == 0) {
            p.setPrecoVenda(p.getPrecoCusto().multiply(new BigDecimal("1.5")));
        }

        return p;
    }

    // UTILS
    private String getVal(String[] dados, Integer idx) {
        if (idx == null || idx < 0 || idx >= dados.length) return "";
        return dados[idx].replace("\"", "").trim();
    }
    private BigDecimal lerDecimal(String val) {
        if (val == null || val.isEmpty()) return BigDecimal.ZERO;
        try {
            val = val.replace("R$", "").trim();
            if (val.contains(",")) val = val.replace(".", "").replace(",", ".");
            return new BigDecimal(val);
        } catch (Exception e) { return BigDecimal.ZERO; }
    }
    private String truncar(String str, int max) {
        if (str == null) return "";
        if (str.length() > max) return str.substring(0, max);
        return str;
    }
    private String normalizarTexto(String str) {
        if (str == null) return "";
        str = str.replace("\uFEFF", "").toUpperCase();
        return Normalizer.normalize(str, Normalizer.Form.NFD).replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").replaceAll("[^A-Z0-9]", "");
    }

    // --- MÉTODOS CRUD E UTILITÁRIOS ---

    public String importarProdutosViaInputStream(InputStream is) { return ""; }
    public String processarExcel(InputStream is) { return ""; }

    @Transactional(readOnly = true)
    public Page<ProdutoListagemDTO> listarResumo(String termo, Pageable pageable) {
        if (pageable == null) pageable = Pageable.unpaged();
        if (termo == null || termo.isBlank()) return produtoRepository.findAll(pageable).map(this::toDTO);

        // --- CORREÇÃO DO ERRO DE COMPILAÇÃO AQUI ---
        // Antes estava passando (termo, termo, pageable). Agora passa apenas (termo, pageable).
        return produtoRepository.findByDescricaoContainingIgnoreCaseOrCodigoBarras(termo, pageable).map(this::toDTO);
    }

    private ProdutoListagemDTO toDTO(Produto p) {
        return new ProdutoListagemDTO(p.getId(), p.getDescricao(), p.getPrecoVenda(), p.getUrlImagem(), p.getQuantidadeEmEstoque(), p.isAtivo(), p.getCodigoBarras(), p.getMarca(), p.getNcm());
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) { return produtoRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Não encontrado")); }

    @Transactional(readOnly = true)
    public List<Produto> listarBaixoEstoque() { return produtoRepository.findProdutosComBaixoEstoque(); }

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistorico(Long id) { return auditoriaService.buscarHistoricoDoProduto(id); }

    @Transactional
    public ProdutoDTO salvar(ProdutoDTO dto) { return new ProdutoDTO(produtoRepository.save(new Produto())); }

    @Transactional
    public Produto salvar(Produto produto) {
        calculadoraFiscalService.aplicarRegrasFiscais(produto);
        return produtoRepository.save(produto);
    }

    @Transactional
    public Produto atualizar(Long id, ProdutoDTO dto) { return produtoRepository.save(buscarPorId(id)); }

    @Transactional
    public void definirPrecoVenda(Long id, BigDecimal p) { Produto prod = buscarPorId(id); prod.setPrecoVenda(p); produtoRepository.save(prod); }

    @Transactional
    public void inativarPorEan(String ean) {
        if (ean == null) throw new IllegalArgumentException("EAN invalido");
        String eanLimpo = ean.trim();

        Produto produto = produtoRepository.findByCodigoBarras(eanLimpo)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + eanLimpo));

        // Define explicitamente como FALSE
        produto.setAtivo(false);

        // CORREÇÃO: Usa saveAndFlush para garantir que o banco receba o dado imediatamente
        produtoRepository.saveAndFlush(produto);

        log.info("Produto inativado: {}", produto.getDescricao());
    }

    @Transactional
    public void reativarPorEan(String ean) { produtoRepository.findByEanIrrestrito(ean).ifPresent(p -> { p.setAtivo(true); produtoRepository.save(p); }); }

    public byte[] gerarRelatorioCsv() { return new byte[0]; }
    public byte[] gerarRelatorioExcel() { return new byte[0]; }

    @Transactional
    public Map<String, Object> saneamentoFiscal() {
        List<Produto> todos = produtoRepository.findAll();
        int alterados = 0;
        List<Produto> paraSalvar = new ArrayList<>();

        for (Produto p : todos) {
            boolean mudou = calculadoraFiscalService.aplicarRegrasFiscais(p);
            if (p.getNcm() != null && p.getNcm().replace(".", "").startsWith("3401")) {
                if (p.getClassificacaoReforma() != TipoTributacaoReforma.REDUZIDA_60) {
                    p.setClassificacaoReforma(TipoTributacaoReforma.REDUZIDA_60);
                    mudou = true;
                }
            }
            if (mudou) {
                paraSalvar.add(p);
                alterados++;
            }
        }
        if (!paraSalvar.isEmpty()) {
            produtoRepository.saveAll(paraSalvar);
        }
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("sucesso", true);
        resultado.put("totalAnalisado", todos.size());
        resultado.put("totalAtualizado", alterados);
        resultado.put("mensagem", "Saneamento concluído! " + alterados + " produtos foram atualizados.");
        return resultado;
    }

    @Transactional
    public Map<String, Object> corrigirNcmsEmMassa() {
        List<Produto> todos = produtoRepository.findAll();
        List<String> logs = new ArrayList<>();
        int corrigidos = 0;

        for (Produto p : todos) {
            String ncmAtual = p.getNcm() == null ? "" : p.getNcm();
            String desc = p.getDescricao().toUpperCase();
            boolean ncmSuspeito = ncmAtual.isEmpty() || ncmAtual.equals("00000000") || ncmAtual.length() < 8 ||
                    (ncmAtual.startsWith("3304") && !desc.contains("BATOM") && !desc.contains("MAKE"));

            if (ncmSuspeito) {
                String ncmInteligente = null;
                String[] palavras = desc.split(" ");
                for (String palavra : palavras) {
                    String palavraLimpa = palavra.replaceAll("[^a-zA-Z0-9]", "");
                    if (palavraLimpa.length() > 3) {
                        ncmInteligente = produtoRepository.findNcmInteligente(palavraLimpa);
                        if (ncmInteligente != null) break;
                    }
                }
                if (ncmInteligente != null && !ncmInteligente.equals(ncmAtual)) {
                    p.setNcm(ncmInteligente);
                    calculadoraFiscalService.aplicarRegrasFiscais(p);
                    produtoRepository.save(p);
                    corrigidos++;
                    logs.add("Produto: " + p.getDescricao() + " | De: " + ncmAtual + " -> Para: " + ncmInteligente);
                }
            }
        }
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("sucesso", true);
        resultado.put("qtdCorrigidos", corrigidos);
        resultado.put("detalhes", logs);
        return resultado;
    }
}