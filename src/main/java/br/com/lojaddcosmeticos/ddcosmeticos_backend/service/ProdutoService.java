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

    // --- IMPORTAÇÃO CSV ---
    public Map<String, Object> importarProdutos(MultipartFile file) {
        Map<String, Object> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("sucesso", false);
            response.put("mensagem", "Arquivo vazio.");
            return response;
        }

        try {
            byte[] bytes = file.getBytes();
            return processarCsvBruto(bytes);
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
                resultado.put("mensagem", "Arquivo sem dados.");
                return resultado;
            }

            String headerLine = linhas[0];
            String delimitador = headerLine.contains(";") ? ";" : ",";
            String[] headers = headerLine.split(delimitador);

            Map<String, Integer> mapa = criarMapaColunasInteligente(headers);

            if (!mapa.containsKey("ean")) {
                resultado.put("sucesso", false);
                resultado.put("mensagem", "ERRO CRÍTICO: Coluna 'EAN' não encontrada.");
                return resultado;
            }

            for (int i = 1; i < linhas.length; i++) {
                String linha = linhas[i].trim();
                if (linha.isEmpty()) continue;
                String[] dados = linha.split(delimitador, -1);

                try {
                    Produto p = criarProdutoDaLinha(dados, mapa);
                    if (p != null) lote.add(p);
                    else listaErros.add("Linha " + (i+1) + ": Ignorada (Sem EAN).");
                } catch (Exception e) {
                    listaErros.add("Linha " + (i+1) + ": Erro (" + e.getMessage() + ")");
                }
            }

            if (!lote.isEmpty()) {
                produtoRepository.saveAll(lote);
                salvos = lote.size();
            }

            resultado.put("sucesso", true);
            resultado.put("qtdImportados", salvos);
            resultado.put("qtdErros", listaErros.size());
            resultado.put("listaErros", listaErros);
            resultado.put("mensagem", listaErros.isEmpty() ?
                    "Sucesso! " + salvos + " importados." :
                    "Importados: " + salvos + ". Falhas: " + listaErros.size());

        } catch (Exception e) {
            e.printStackTrace();
            resultado.put("sucesso", false);
            resultado.put("mensagem", "Erro crítico: " + e.getMessage());
        }
        return resultado;
    }

    private Map<String, Integer> criarMapaColunasInteligente(String[] headers) {
        Map<String, Integer> mapa = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String h = normalizarTexto(headers[i]);
            if (h.contains("EAN") || h.contains("BARRAS") || h.contains("GTIN") || h.equals("CODIGO")) mapa.put("ean", i);
            else if ((h.contains("DESC") || h.contains("NOME")) && !h.contains("CATEGORIA")) mapa.put("desc", i);
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
        }
        return mapa;
    }

    private Produto criarProdutoDaLinha(String[] dados, Map<String, Integer> mapa) {
        if (!mapa.containsKey("ean")) return null;
        String ean = getVal(dados, mapa.get("ean")).replaceAll("[^0-9]", "");
        if (ean.isEmpty()) return null;

        Produto p = produtoRepository.findByEanIrrestrito(ean).orElse(new Produto());

        if (p.getId() == null) {
            p.setCodigoBarras(ean);
            p.setAtivo(true);
            p.setOrigem("0"); p.setCst("102"); p.setNcm("00000000");
            p.setQuantidadeEmEstoque(0); p.setEstoqueFiscal(0); p.setEstoqueNaoFiscal(0);
        } else {
            if (!mapa.containsKey("ativo")) {
                p.setAtivo(true);
            }
        }

        if (mapa.containsKey("desc")) {
            String desc = getVal(dados, mapa.get("desc")).toUpperCase();
            if (!desc.isEmpty()) p.setDescricao(truncar(desc, 250));
        }
        if (p.getDescricao() == null || p.getDescricao().trim().isEmpty()) p.setDescricao("PRODUTO " + ean);

        if (mapa.containsKey("custo")) p.setPrecoCusto(lerDecimal(getVal(dados, mapa.get("custo"))));
        if (mapa.containsKey("venda")) p.setPrecoVenda(lerDecimal(getVal(dados, mapa.get("venda"))));

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

        if (mapa.containsKey("marca")) p.setMarca(truncar(getVal(dados, mapa.get("marca")).toUpperCase(), 50));
        if (mapa.containsKey("cat")) p.setCategoria(truncar(getVal(dados, mapa.get("cat")), 50));
        if (mapa.containsKey("sub")) p.setSubcategoria(truncar(getVal(dados, mapa.get("sub")), 50));
        if (mapa.containsKey("unidade")) p.setUnidade(truncar(getVal(dados, mapa.get("unidade")), 10));

        if (mapa.containsKey("ativo")) {
            String a = getVal(dados, mapa.get("ativo")).toUpperCase();
            p.setAtivo(a.startsWith("S") || a.equals("1") || a.equals("TRUE"));
        }

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
                    ncmInteligente = produtoRepository.findNcmInteligente(pl);
                    if (ncmInteligente != null) break;
                }
            }
            if (ncmInteligente != null) p.setNcm(ncmInteligente);
            else if (!ncmArquivo.isEmpty()) p.setNcm(ncmArquivo);
        }

        if (mapa.containsKey("cest")) p.setCest(truncar(getVal(dados, mapa.get("cest")).replaceAll("[^0-9]", ""), 7));
        if (mapa.containsKey("origem")) {
            String o = getVal(dados, mapa.get("origem")).replaceAll("[^0-9]", "");
            if(!o.isEmpty()) p.setOrigem(o.substring(0, 1));
        }

        if (p.getNcm() != null && !p.getNcm().equals("00000000")) {
            try { calculadoraFiscalService.aplicarRegrasFiscais(p); } catch (Exception ignored) {}
        }

        if (p.getPrecoCusto() == null) p.setPrecoCusto(BigDecimal.ZERO);
        if (p.getPrecoVenda() == null || p.getPrecoVenda().compareTo(BigDecimal.ZERO) == 0) {
            p.setPrecoVenda(p.getPrecoCusto().multiply(new BigDecimal("1.5")));
        }

        return p;
    }

    private String getVal(String[] dados, Integer idx) { return (idx == null || idx < 0 || idx >= dados.length) ? "" : dados[idx].replace("\"", "").trim(); }

    // --- CORREÇÃO DE ESCALA DECIMAL ---
    private BigDecimal lerDecimal(String val) {
        if (val == null || val.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            val = val.replace("R$", "").trim();

            // LÓGICA INTELIGENTE:
            // Se tem vírgula, é formato PT-BR (1.000,00) -> Remove ponto de milhar, troca vírgula por ponto
            if (val.contains(",")) {
                val = val.replace(".", "").replace(",", ".");
            }
            // Se NÃO tem vírgula, mas tem ponto, assume que é decimal (1000.00) ou sem separador (1000)
            // Nesse caso, NÃO removemos o ponto, pois ele é o separador decimal.

            return new BigDecimal(val);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String truncar(String str, int max) { return (str == null || str.length() <= max) ? (str == null ? "" : str) : str.substring(0, max); }
    private String normalizarTexto(String str) { return str == null ? "" : Normalizer.normalize(str.replace("\uFEFF", "").toUpperCase(), Normalizer.Form.NFD).replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").replaceAll("[^A-Z0-9]", ""); }

    // --- MÉTODOS CRUD E FILTROS ---

    @Transactional(readOnly = true)
    public Page<ProdutoListagemDTO> listarResumo(
            String termo,
            String marca,
            String categoria,
            String statusEstoque,
            Boolean semImagem,
            Boolean semNcm,
            Boolean precoZero,
            Pageable pageable
    ) {
        if (pageable == null) pageable = Pageable.unpaged();

        if (termo != null && termo.trim().isEmpty()) termo = null;
        if (marca != null && marca.trim().isEmpty()) marca = null;
        if (categoria != null && categoria.trim().isEmpty()) categoria = null;
        if (statusEstoque != null && (statusEstoque.trim().isEmpty() || "todos".equalsIgnoreCase(statusEstoque))) statusEstoque = null;

        if (semImagem == null) semImagem = false;
        if (semNcm == null) semNcm = false;
        if (precoZero == null) precoZero = false;

        return produtoRepository.buscarComFiltros(
                termo, marca, categoria, statusEstoque, semImagem, semNcm, precoZero, pageable
        ).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProdutoListagemDTO> listarResumo(String termo, Pageable pageable) {
        return listarResumo(termo, null, null, null, false, false, false, pageable);
    }

    private ProdutoListagemDTO toDTO(Produto p) {
        return new ProdutoListagemDTO(p.getId(), p.getDescricao(), p.getPrecoVenda(), p.getUrlImagem(), p.getQuantidadeEmEstoque(), p.isAtivo(), p.getCodigoBarras(), p.getMarca(), p.getNcm());
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) { return produtoRepository.findById(id).orElseThrow(()->new ResourceNotFoundException("Não encontrado")); }

    @Transactional(readOnly = true)
    public List<Produto> buscarLixeira() {
        return produtoRepository.findAllLixeira();
    }

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
        produto.setAtivo(false);
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
            if (p.getNcm() != null && p.getNcm().startsWith("3401") && p.getClassificacaoReforma() != TipoTributacaoReforma.REDUZIDA_60) {
                p.setClassificacaoReforma(TipoTributacaoReforma.REDUZIDA_60);
                mudou = true;
            }
            if (mudou) {
                paraSalvar.add(p);
                alterados++;
            }
        }
        if (!paraSalvar.isEmpty()) produtoRepository.saveAll(paraSalvar);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("sucesso", true);
        resultado.put("totalAtualizado", alterados);
        return resultado;
    }

    @Transactional
    public Map<String, Object> corrigirNcmsEmMassa() {
        List<Produto> todos = produtoRepository.findAll();
        List<String> logs = new ArrayList<>();
        int corrigidos = 0;

        for (Produto p : todos) {
            String ncm = p.getNcm() == null ? "" : p.getNcm();
            if (ncm.isEmpty() || ncm.equals("00000000")) {
                String ncmNovo = produtoRepository.findNcmInteligente(p.getDescricao().split(" ")[0]);
                if (ncmNovo != null) {
                    p.setNcm(ncmNovo);
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
}