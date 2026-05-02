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

    // --- CONSTANTES FINANCEIRAS OTIMIZADAS PARA PERFORMANCE ---
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final BigDecimal ALIQ_IBS_PADRAO = new BigDecimal("17.5");
    private static final BigDecimal ALIQ_CBS_PADRAO = new BigDecimal("9.0");
    private static final BigDecimal ALIQ_TOTAL_PADRAO = ALIQ_IBS_PADRAO.add(ALIQ_CBS_PADRAO).divide(CEM, 4, RoundingMode.HALF_EVEN);
    private static final BigDecimal FATOR_REDUCAO_60 = new BigDecimal("0.4");
    private static final BigDecimal ALIQ_INTERNA_DESTINO = new BigDecimal("0.205");
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
        MAPA_INTELIGENCIA.put("SABAO", sabonete);

        DadosFiscais desodorante = new DadosFiscais("33072010", "2003700");
        MAPA_INTELIGENCIA.put("DESODORANTE", desodorante);
        MAPA_INTELIGENCIA.put("ANTITRANSPIRANTE", desodorante);

        // MAQUIAGEM & UNHAS
        MAPA_INTELIGENCIA.put("BATOM", new DadosFiscais("33041000", "2001300"));
        MAPA_INTELIGENCIA.put("RIMEL", new DadosFiscais("33042010", "2001400"));
        MAPA_INTELIGENCIA.put("ESMALTE", new DadosFiscais("33043000", "2001500"));
        MAPA_INTELIGENCIA.put("BASE UNHA", new DadosFiscais("33043000", "2001500"));
    }

    /**
     * 🔥 RETORNA O SEU ValidacaoFiscalDTO (Classe com Lombok)
     */
    public ValidacaoFiscalDTO simularValidacao(String descricao, String ncmDigitado) {
        return processarInteligencia(descricao, ncmDigitado);
    }

    public boolean aplicarRegrasFiscais(Produto p) {
        ValidacaoFiscalDTO dados = processarInteligencia(p.getDescricao(), p.getNcm());
        boolean alterou = false;

        if (!Objects.equals(p.getNcm(), dados.getNcm())) { p.setNcm(dados.getNcm()); alterou = true; }
        if (!Objects.equals(p.getCest(), dados.getCest())) { p.setCest(dados.getCest()); alterou = true; }
        if (!Objects.equals(p.getCst(), dados.getCst())) { p.setCst(dados.getCst()); alterou = true; }

        // 🚨 OTIMIZAÇÃO: Chamando o getter protegido que criámos na entidade Produto
        if (!Objects.equals(p.verificarSeMonofasico(), dados.isMonofasico())) {
            p.setIsMonofasico(dados.isMonofasico());
            alterou = true;
        }

        RegraFiscalResultado regrasCalculadas = calcularRegras(p.getNcm());
        if (p.getClassificacaoReforma() != regrasCalculadas.classificacaoReforma()) {
            p.setClassificacaoReforma(regrasCalculadas.classificacaoReforma());
            alterou = true;
        }

        return alterou;
    }

    /**
     * 🔥 Agora monta e retorna a sua classe ValidacaoFiscalDTO
     */
    private ValidacaoFiscalDTO processarInteligencia(String descricao, String ncmDigitado) {
        String descLimpa = limparString(descricao);
        String ncmFinal = (ncmDigitado != null) ? ncmDigitado.replaceAll("\\D", "") : "";
        String cestFinal = "";

        boolean achouPelaDescricao = false;
        for (Map.Entry<String, DadosFiscais> regra : MAPA_INTELIGENCIA.entrySet()) {
            if (descLimpa.matches(".*\\b" + regra.getKey() + "\\b.*")) {
                ncmFinal = regra.getValue().ncm();
                cestFinal = regra.getValue().cest();
                achouPelaDescricao = true;
                break;
            }
        }

        if (!achouPelaDescricao && !ncmFinal.isEmpty()) {
            cestFinal = inferirCestPorNcm(ncmFinal);
        }

        RegraFiscalResultado regras = calcularRegras(ncmFinal);

        // Utilizando os Setters do seu @Data (Lombok)
        ValidacaoFiscalDTO dto = new ValidacaoFiscalDTO();
        dto.setNcm(ncmFinal.isEmpty() ? "00000000" : ncmFinal);
        dto.setCest(cestFinal == null ? "" : cestFinal);
        dto.setCst(regras.cst());
        dto.setMonofasico(regras.monofasico());
        dto.setImpostoSeletivo(regras.impostoSeletivo());

        return dto;
    }

    // ==================================================================================
    // MÓDULO 2: CÁLCULOS TRIBUTÁRIOS
    // ==================================================================================

    private record RegraFiscalResultado(boolean monofasico, String cst, TipoTributacaoReforma classificacaoReforma, boolean impostoSeletivo) {}

    private RegraFiscalResultado calcularRegras(String ncm) {
        if (ncm == null) ncm = "";
        String ncmLimpo = ncm.replaceAll("\\D", "");

        boolean isMonofasico = ncmLimpo.startsWith("3303") || ncmLimpo.startsWith("3304") ||
                ncmLimpo.startsWith("3305") || ncmLimpo.startsWith("3307") ||
                ncmLimpo.startsWith("3401");

        String cst = "102";

        String cestInferido = inferirCestPorNcm(ncmLimpo);
        if (!cestInferido.isEmpty() || isMonofasico) {
            cst = "500";
        }

        TipoTributacaoReforma classificacao = TipoTributacaoReforma.PADRAO;
        boolean impostoSeletivo = false;

        if (ncmLimpo.startsWith("33") || ncmLimpo.startsWith("3401") ||
                ncmLimpo.startsWith("9603") || ncmLimpo.startsWith("9619")) {
            classificacao = TipoTributacaoReforma.REDUZIDA_60;
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

            // 🚨 SEGURANÇA: Usando o método blindado getValorTotalItem da entidade ItemVenda
            BigDecimal subtotal = item.getValorTotalItem();

            BigDecimal aliqIbs = ALIQ_IBS_PADRAO;
            BigDecimal aliqCbs = ALIQ_CBS_PADRAO;
            BigDecimal aliqIs = BigDecimal.ZERO;

            TipoTributacaoReforma classificacao = p.getClassificacaoReforma() != null ? p.getClassificacaoReforma() : TipoTributacaoReforma.PADRAO;

            if (classificacao == TipoTributacaoReforma.REDUZIDA_60) {
                aliqIbs = aliqIbs.multiply(FATOR_REDUCAO_60);
                aliqCbs = aliqCbs.multiply(FATOR_REDUCAO_60);
            } else if (classificacao == TipoTributacaoReforma.CESTA_BASICA || classificacao == TipoTributacaoReforma.IMUNE) {
                aliqIbs = BigDecimal.ZERO;
                aliqCbs = BigDecimal.ZERO;
            }

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
        BigDecimal aliqIbs = ALIQ_IBS_PADRAO.divide(CEM, 4, RoundingMode.HALF_EVEN);
        BigDecimal aliqCbs = ALIQ_CBS_PADRAO.divide(CEM, 4, RoundingMode.HALF_EVEN);

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
        return "Simulação: Para o faturamento em regime ME, o modelo atual preserva mais a margem.";
    }

    public SplitPaymentDTO calcularSplitPayment(List<ItemVenda> itens) {
        BigDecimal totalVenda = BigDecimal.ZERO;
        BigDecimal totalImposto = BigDecimal.ZERO;

        for (ItemVenda item : itens) {
            BigDecimal subtotal = item.getValorTotalItem();
            totalVenda = totalVenda.add(subtotal);

            BigDecimal aliqAplicavel = ALIQ_TOTAL_PADRAO;
            TipoTributacaoReforma classificacao = item.getProduto().getClassificacaoReforma() != null ? item.getProduto().getClassificacaoReforma() : TipoTributacaoReforma.PADRAO;

            if (classificacao == TipoTributacaoReforma.REDUZIDA_60) {
                aliqAplicavel = aliqAplicavel.multiply(FATOR_REDUCAO_60);
            } else if (classificacao == TipoTributacaoReforma.CESTA_BASICA || classificacao == TipoTributacaoReforma.IMUNE) {
                aliqAplicavel = BigDecimal.ZERO;
            }

            if (!item.getProduto().verificarSeMonofasico()) {
                totalImposto = totalImposto.add(subtotal.multiply(aliqAplicavel));
            }
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
        BigDecimal mvaDecimal = (mvaPercentual != null ? mvaPercentual : BigDecimal.ZERO).divide(CEM, 4, RoundingMode.HALF_EVEN);

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
        if (ncm.startsWith("330410")) return "2001300";
        if (ncm.startsWith("330420")) return "2001400";
        if (ncm.startsWith("330430")) return "2001500";
        if (ncm.startsWith("330499")) return "2001600";
        if (ncm.startsWith("340111")) return "2003300";
        if (ncm.startsWith("330720")) return "2003700";
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