package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoExternoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoFiscalCarrinhoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SplitPaymentDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ValidacaoFiscalDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.RegraTributariaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class CalculadoraFiscalService {

    @Autowired
    private RegraTributariaRepository regraRepository;

    // --- CONSTANTES FINANCEIRAS OTIMIZADAS PARA PERFORMANCE ---
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal ALIQ_IBS_PADRAO = new BigDecimal("17.5");
    private static final BigDecimal ALIQ_CBS_PADRAO = new BigDecimal("9.0");
    private static final BigDecimal ALIQ_TOTAL_PADRAO = new BigDecimal("0.265");
    private static final BigDecimal FATOR_REDUCAO_60 = new BigDecimal("0.4");
    private static final BigDecimal ALIQ_INTERNA_DESTINO = new BigDecimal("0.205"); // Média 20.5%
    private static final Set<String> ESTADOS_7_PORCENTO = Set.of("SP", "MG", "RJ", "RS", "SC", "PR");
    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    // ==================================================================================
    // MÓDULO 1: INTELIGÊNCIA DE CADASTRO (DESCRIÇÃO -> NCM/CEST)
    // ==================================================================================

    private record DadosFiscais(String ncm, String cest) {}
    private static final Map<String, DadosFiscais> MAPA_INTELIGENCIA = new HashMap<>();

    static {
        // CABELOS
        DadosFiscais shampoo = new DadosFiscais("33051000", "2002100");
        MAPA_INTELIGENCIA.put("SHAMPOO", shampoo);
        MAPA_INTELIGENCIA.put("XAMPU", shampoo);

        DadosFiscais capilares = new DadosFiscais("33059000", "2002400");
        MAPA_INTELIGENCIA.put("CONDICIONADOR", capilares);
        MAPA_INTELIGENCIA.put("MASCARA", capilares);
        MAPA_INTELIGENCIA.put("CREME", capilares);
        MAPA_INTELIGENCIA.put("LEAVE", capilares);
        MAPA_INTELIGENCIA.put("TINTURA", capilares);

        // CORPO
        DadosFiscais sabonete = new DadosFiscais("34011190", "2003300");
        MAPA_INTELIGENCIA.put("SABONETE", sabonete);

        DadosFiscais desodorante = new DadosFiscais("33072010", "2003700");
        MAPA_INTELIGENCIA.put("DESODORANTE", desodorante);

        // MAQUIAGEM & UNHAS
        MAPA_INTELIGENCIA.put("BATOM", new DadosFiscais("33041000", "2001300"));
        MAPA_INTELIGENCIA.put("RIMEL", new DadosFiscais("33042010", "2001400"));
        MAPA_INTELIGENCIA.put("ESMALTE", new DadosFiscais("33043000", "2001500"));
        MAPA_INTELIGENCIA.put("BASE UNHA", new DadosFiscais("33043000", "2001500"));
    }

    public ValidacaoFiscalDTO.Response simularValidacao(String descricao, String ncmDigitado) {
        return processarInteligencia(descricao, ncmDigitado);
    }

    public boolean aplicarRegrasFiscais(Produto p) {
        ValidacaoFiscalDTO.Response dados = processarInteligencia(p.getDescricao(), p.getNcm());
        boolean alterou = false;

        if (!Objects.equals(p.getNcm(), dados.ncm())) { p.setNcm(dados.ncm()); alterou = true; }
        if (!Objects.equals(p.getCest(), dados.cest())) { p.setCest(dados.cest()); alterou = true; }
        if (!Objects.equals(p.getCst(), dados.cst())) { p.setCst(dados.cst()); alterou = true; } // Correção de setCest para setCst
        if (p.isMonofasico() != dados.monofasico()) { p.setIsMonofasico(dados.monofasico()); alterou = true; }

        RegraFiscalResultado regrasCalculadas = calcularRegras(p.getNcm());

        if (p.getClassificacaoReforma() != regrasCalculadas.classificacaoReforma()) {
            p.setClassificacaoReforma(regrasCalculadas.classificacaoReforma());
            alterou = true;
        }

        return alterou;
    }

    private ValidacaoFiscalDTO.Response processarInteligencia(String descricao, String ncmDigitado) {
        String descLimpa = limparString(descricao);
        String ncmFinal = (ncmDigitado != null) ? ncmDigitado.replaceAll("\\D", "") : "";
        String cestFinal = "";

        boolean achou = false;
        for (Map.Entry<String, DadosFiscais> regra : MAPA_INTELIGENCIA.entrySet()) {
            if (descLimpa.contains(regra.getKey())) {
                ncmFinal = regra.getValue().ncm();
                cestFinal = regra.getValue().cest();
                achou = true;
                break;
            }
        }

        if (!achou && !ncmFinal.isEmpty()) {
            cestFinal = inferirCestPorNcm(ncmFinal);
        }

        RegraFiscalResultado regras = calcularRegras(ncmFinal);

        return new ValidacaoFiscalDTO.Response(
                ncmFinal.isEmpty() ? "00000000" : ncmFinal,
                cestFinal == null ? "" : cestFinal,
                regras.cst(),
                regras.monofasico(),
                regras.impostoSeletivo(),
                "0"
        );
    }

    // ==================================================================================
    // MÓDULO 2: CÁLCULOS TRIBUTÁRIOS VENDA (LC 214 / REFORMA)
    // ==================================================================================

    private record RegraFiscalResultado(boolean monofasico, String cst, TipoTributacaoReforma classificacaoReforma, boolean impostoSeletivo) {}

    private RegraFiscalResultado calcularRegras(String ncm) {
        if (ncm == null) ncm = "";
        String ncmLimpo = ncm.replaceAll("\\D", ""); // Mais seguro que replace(".", "")

        boolean isMonofasico = ncmLimpo.startsWith("3303") || ncmLimpo.startsWith("3304") ||
                ncmLimpo.startsWith("3305") || ncmLimpo.startsWith("3307") ||
                ncmLimpo.startsWith("3401");

        String cst = isMonofasico ? "04" : "00";

        TipoTributacaoReforma classificacao = TipoTributacaoReforma.PADRAO;
        boolean impostoSeletivo = false;

        if (ncmLimpo.startsWith("3003") || ncmLimpo.startsWith("3004") ||
                ncmLimpo.startsWith("3303") || ncmLimpo.startsWith("3304") ||
                ncmLimpo.startsWith("3305") || ncmLimpo.startsWith("3306") ||
                ncmLimpo.startsWith("3307") || ncmLimpo.startsWith("3401") ||
                ncmLimpo.startsWith("9603") || ncmLimpo.startsWith("9619")) {
            classificacao = TipoTributacaoReforma.REDUZIDA_60;
        } else if (ncmLimpo.startsWith("0401") || ncmLimpo.startsWith("1006")) {
            classificacao = TipoTributacaoReforma.CESTA_BASICA;
        }

        return new RegraFiscalResultado(isMonofasico, cst, classificacao, impostoSeletivo);
    }

    public ResumoFiscalCarrinhoDTO calcularTotaisCarrinho(List<ItemVenda> itens) {
        BigDecimal totalVenda = BigDecimal.ZERO;
        BigDecimal somaIbs = BigDecimal.ZERO;
        BigDecimal somaCbs = BigDecimal.ZERO;
        BigDecimal somaIs = BigDecimal.ZERO;

        for (ItemVenda item : itens) {
            Produto p = item.getProduto();
            BigDecimal qtd = (item.getQuantidade() != null) ? new BigDecimal(item.getQuantidade().toString()) : BigDecimal.ZERO;
            BigDecimal subtotal = item.getPrecoUnitario().multiply(qtd);

            BigDecimal aliqIbs = ALIQ_IBS_PADRAO;
            BigDecimal aliqCbs = ALIQ_CBS_PADRAO;
            BigDecimal aliqIs = BigDecimal.ZERO;

            TipoTributacaoReforma classificacao = p.getClassificacaoReforma() != null ? p.getClassificacaoReforma() : TipoTributacaoReforma.PADRAO;

            if (classificacao == TipoTributacaoReforma.REDUZIDA_60) {
                aliqIbs = aliqIbs.multiply(FATOR_REDUCAO_60);
                aliqCbs = aliqCbs.multiply(FATOR_REDUCAO_60);
            } else if (classificacao == TipoTributacaoReforma.CESTA_BASICA) {
                aliqIbs = BigDecimal.ZERO;
                aliqCbs = BigDecimal.ZERO;
            }

            // Otimizado: Dividir por CEM com arredondamento seguro
            somaIbs = somaIbs.add(subtotal.multiply(aliqIbs).divide(CEM, 2, RoundingMode.HALF_EVEN));
            somaCbs = somaCbs.add(subtotal.multiply(aliqCbs).divide(CEM, 2, RoundingMode.HALF_EVEN));
            somaIs = somaIs.add(subtotal.multiply(aliqIs).divide(CEM, 2, RoundingMode.HALF_EVEN));
            totalVenda = totalVenda.add(subtotal);
        }

        BigDecimal totalImpostos = somaIbs.add(somaCbs).add(somaIs);
        return new ResumoFiscalCarrinhoDTO(totalVenda, somaIbs, somaCbs, somaIs, totalVenda.subtract(totalImpostos), BigDecimal.ZERO);
    }

    // ==================================================================================
    // MÓDULO 3: SIMULAÇÕES E SPLIT PAYMENT
    // ==================================================================================

    public Map<String, BigDecimal> simularTributacao2026(BigDecimal valorVenda, boolean isMonofasico) {
        BigDecimal aliqIbs = new BigDecimal("0.175");
        BigDecimal aliqCbs = new BigDecimal("0.090");

        if (isMonofasico) {
            aliqIbs = BigDecimal.ZERO;
            aliqCbs = BigDecimal.ZERO;
        }

        Map<String, BigDecimal> simulacao = new HashMap<>();
        BigDecimal valorIBS = valorVenda.multiply(aliqIbs);
        BigDecimal valorCBS = valorVenda.multiply(aliqCbs);

        simulacao.put("IBS_VALOR", valorIBS.setScale(2, RoundingMode.HALF_EVEN));
        simulacao.put("CBS_VALOR", valorCBS.setScale(2, RoundingMode.HALF_EVEN));
        simulacao.put("TOTAL_NOVO", valorIBS.add(valorCBS).setScale(2, RoundingMode.HALF_EVEN));

        return simulacao;
    }

    public String analisarCenarioMaisVantajoso(BigDecimal faturamentoMensal, BigDecimal comprasMensais) {
        return "Simulação: Para faturamento de " + faturamentoMensal + ", o regime atual ainda é vantajoso até 2027.";
    }

    public SplitPaymentDTO calcularSplitPayment(List<ItemVenda> itens) {
        BigDecimal totalVenda = BigDecimal.ZERO;
        BigDecimal totalImposto = BigDecimal.ZERO;

        for (ItemVenda item : itens) {
            BigDecimal subtotal = item.getPrecoUnitario().multiply(new BigDecimal(item.getQuantidade().toString()));
            totalVenda = totalVenda.add(subtotal);

            BigDecimal aliqTotal = ALIQ_TOTAL_PADRAO;
            TipoTributacaoReforma classificacao = item.getProduto().getClassificacaoReforma() != null ? item.getProduto().getClassificacaoReforma() : TipoTributacaoReforma.PADRAO;

            if (classificacao == TipoTributacaoReforma.REDUZIDA_60) {
                aliqTotal = aliqTotal.multiply(FATOR_REDUCAO_60);
            } else if (classificacao == TipoTributacaoReforma.CESTA_BASICA) {
                aliqTotal = BigDecimal.ZERO;
            }
            totalImposto = totalImposto.add(subtotal.multiply(aliqTotal));
        }

        BigDecimal liquido = totalVenda.subtract(totalImposto);
        BigDecimal aliqEfetiva = (totalVenda.compareTo(BigDecimal.ZERO) > 0)
                ? totalImposto.divide(totalVenda, 4, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO;

        return new SplitPaymentDTO(totalVenda, liquido, totalImposto, aliqEfetiva, "Simulação Split Payment 2026");
    }

    // ==================================================================================
    // MÓDULO 4: CÁLCULOS DE COMPRA (ICMS-ST / MVA)
    // ==================================================================================

    public BigDecimal calcularImposto(BigDecimal valorProduto, BigDecimal mvaPercentual, String ufOrigem, String ufDestino) {
        if (ufOrigem == null || ufDestino == null || ufOrigem.equalsIgnoreCase(ufDestino)) {
            return BigDecimal.ZERO;
        }

        BigDecimal aliqInterestadual = obterAliquotaInterestadual(ufOrigem, ufDestino);

        BigDecimal mvaDecimal = (mvaPercentual != null ? mvaPercentual : BigDecimal.ZERO)
                .divide(CEM, 4, RoundingMode.HALF_EVEN);

        BigDecimal baseCalculoSt = valorProduto.multiply(BigDecimal.ONE.add(mvaDecimal));
        BigDecimal debitoDestino = baseCalculoSt.multiply(ALIQ_INTERNA_DESTINO);
        BigDecimal creditoOrigem = valorProduto.multiply(aliqInterestadual);

        return debitoDestino.subtract(creditoOrigem).max(BigDecimal.ZERO);
    }

    private BigDecimal obterAliquotaInterestadual(String origem, String destino) {
        if (ESTADOS_7_PORCENTO.contains(origem.toUpperCase()) && !ESTADOS_7_PORCENTO.contains(destino.toUpperCase())) {
            return new BigDecimal("0.07");
        }
        return new BigDecimal("0.12");
    }

    // --- UTILITÁRIOS ---

    private String inferirCestPorNcm(String ncm) {
        if (ncm == null) return "";
        if (ncm.startsWith("330510")) return "2002100";
        if (ncm.startsWith("330590")) return "2002400";
        if (ncm.startsWith("3304")) return "2001600";
        if (ncm.startsWith("3401")) return "2003300";
        return "";
    }

    private String limparString(String s) {
        if (s == null) return "";
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(nfd).replaceAll("").toUpperCase().trim();
    }

    public void aplicarRegras(ProdutoExternoDTO dto) {
        RegraFiscalResultado r = calcularRegras(dto.getNcm() != null ? dto.getNcm() : "");
        dto.setMonofasico(r.monofasico());
        dto.setCst(r.cst());
        dto.setClassificacaoReforma(r.classificacaoReforma().name());
    }
}