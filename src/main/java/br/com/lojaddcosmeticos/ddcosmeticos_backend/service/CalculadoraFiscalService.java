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

        // Atualiza NCM se a IA achou algo melhor
        if (!Objects.equals(p.getNcm(), dados.ncm())) { p.setNcm(dados.ncm()); alterou = true; }
        // Atualiza CEST
        if (!Objects.equals(p.getCest(), dados.cest())) { p.setCest(dados.cest()); alterou = true; }
        // Atualiza CST (04 Monofásico / 00 Tributado)
        if (!Objects.equals(p.getCst(), dados.cst())) { p.setCest(dados.cst()); alterou = true; }
        // Atualiza flag Monofásico
        if (p.isMonofasico() != dados.monofasico()) { p.setMonofasico(dados.monofasico()); alterou = true; }

        // --- CORREÇÃO IMPORTANTE: Atualiza a Reforma Tributária baseado no retorno da Regra ---
        // Antes estava apenas setando PADRAO se fosse nulo. Agora recalcula sempre que mudar o NCM.
        RegraFiscalResultado regrasCalculadas = calcularRegras(p.getNcm());

        // LOG DE DEBUG
        if (p.getNcm().startsWith("3401")) {
            System.out.println("Produto: " + p.getDescricao() + " | NCM: " + p.getNcm() + " | Nova Classificação: " + regrasCalculadas.classificacaoReforma());
        }

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
        String ncmLimpo = ncm.replace(".", "").trim();

        // 1. Lógica do Monofásico (PIS/COFINS Atual)
        // Cosméticos (Cap 33), Sabões (3401)
        boolean isMonofasico = ncmLimpo.startsWith("3303") || ncmLimpo.startsWith("3304") ||
                ncmLimpo.startsWith("3305") || ncmLimpo.startsWith("3307") ||
                ncmLimpo.startsWith("3401"); // Sabonetes

        String cst = isMonofasico ? "04" : "00"; // 04 = Monofásico, 00 = Tributado

        // 2. Lógica da Reforma Tributária (IBS/CBS - LC 214)
        TipoTributacaoReforma classificacao = TipoTributacaoReforma.PADRAO;
        boolean impostoSeletivo = false;

        // --- LISTA DE ALÍQUOTA REDUZIDA (60%) ---
        // Itens de Higiene Pessoal, Limpeza e Cuidados Básicos de Saúde
        if (
            // Cap 30: Medicamentos e Farmacêuticos
                ncmLimpo.startsWith("3003") || ncmLimpo.startsWith("3004") ||
                        // Cap 33: Óleos, Perfumaria e Cosméticos
                        ncmLimpo.startsWith("3303") || // Perfumes
                        ncmLimpo.startsWith("3304") || // Beleza/Maquiagem/Cremes
                        ncmLimpo.startsWith("3305") || // Capilares (Shampoo/Condicionador)
                        ncmLimpo.startsWith("3306") || // Bucal (Pasta/Fio)
                        ncmLimpo.startsWith("3307") || // Barbear/Desodorantes
                        // Cap 34: Sabonetes e Agentes Orgânicos
                        ncmLimpo.startsWith("3401") || // Sabonetes (EM BARRA OU LIQUIDO)
                        // Cap 96: Diversos
                        ncmLimpo.startsWith("9603") || // Escovas de dentes
                        ncmLimpo.startsWith("9619")    // Absorventes e Fraldas
        ) {
            classificacao = TipoTributacaoReforma.REDUZIDA_60;
        }
        // Exceção: Cesta Básica Nacional (Se houver produtos alimentícios ou muito básicos)
        else if (ncmLimpo.startsWith("0401") || ncmLimpo.startsWith("1006")) {
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

            // Alíquotas Padrão Estimadas (26.5% Total)
            BigDecimal aliqIbs = new BigDecimal("17.5"); // Estado/Município
            BigDecimal aliqCbs = new BigDecimal("9.0");  // União
            BigDecimal aliqIs = BigDecimal.ZERO;

            // Aplica Redutor de 60% se for Higiene/Saúde
            if (p.getClassificacaoReforma() == TipoTributacaoReforma.REDUZIDA_60) {
                // Paga apenas 40% da alíquota cheia
                aliqIbs = aliqIbs.multiply(new BigDecimal("0.4"));
                aliqCbs = aliqCbs.multiply(new BigDecimal("0.4"));
            } else if (p.getClassificacaoReforma() == TipoTributacaoReforma.CESTA_BASICA) {
                aliqIbs = BigDecimal.ZERO;
                aliqCbs = BigDecimal.ZERO;
            }

            somaIbs = somaIbs.add(subtotal.multiply(aliqIbs).divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN));
            somaCbs = somaCbs.add(subtotal.multiply(aliqCbs).divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN));
            somaIs = somaIs.add(subtotal.multiply(aliqIs).divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN));
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

        simulacao.put("IBS_VALOR", valorIBS.setScale(2, RoundingMode.HALF_UP));
        simulacao.put("CBS_VALOR", valorCBS.setScale(2, RoundingMode.HALF_UP));
        simulacao.put("TOTAL_NOVO", valorIBS.add(valorCBS).setScale(2, RoundingMode.HALF_UP));

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

            BigDecimal aliqTotal = new BigDecimal("0.265");
            if (item.getProduto().getClassificacaoReforma() == TipoTributacaoReforma.REDUZIDA_60) {
                aliqTotal = aliqTotal.multiply(new BigDecimal("0.4"));
            } else if (item.getProduto().getClassificacaoReforma() == TipoTributacaoReforma.CESTA_BASICA) {
                aliqTotal = BigDecimal.ZERO;
            }
            totalImposto = totalImposto.add(subtotal.multiply(aliqTotal));
        }

        BigDecimal liquido = totalVenda.subtract(totalImposto);
        BigDecimal aliqEfetiva = (totalVenda.compareTo(BigDecimal.ZERO) > 0)
                ? totalImposto.divide(totalVenda, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new SplitPaymentDTO(totalVenda, liquido, totalImposto, aliqEfetiva, "Simulação Split Payment 2026");
    }

    // ==================================================================================
    // MÓDULO 4: CÁLCULOS DE COMPRA (ICMS-ST / MVA)
    // ==================================================================================

    private static final BigDecimal ALIQ_INTERNA_DESTINO = new BigDecimal("0.205"); // Média 20.5%
    private static final Set<String> ESTADOS_7_PORCENTO = Set.of("SP", "MG", "RJ", "RS", "SC", "PR");

    public BigDecimal calcularImposto(BigDecimal valorProduto, BigDecimal mvaPercentual, String ufOrigem, String ufDestino) {
        if (ufOrigem == null || ufDestino == null || ufOrigem.equalsIgnoreCase(ufDestino)) {
            return BigDecimal.ZERO;
        }

        BigDecimal aliqInterestadual = obterAliquotaInterestadual(ufOrigem, ufDestino);

        // Cálculo do ICMS ST
        BigDecimal mvaDecimal = (mvaPercentual != null ? mvaPercentual : BigDecimal.ZERO)
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

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
        return Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(nfd).replaceAll("").toUpperCase().trim();
    }

    public void aplicarRegras(ProdutoExternoDTO dto) {
        RegraFiscalResultado r = calcularRegras(dto.getNcm() != null ? dto.getNcm() : "");
        dto.setMonofasico(r.monofasico());
        dto.setCst(r.cst());
        dto.setClassificacaoReforma(r.classificacaoReforma().name());
    }
}