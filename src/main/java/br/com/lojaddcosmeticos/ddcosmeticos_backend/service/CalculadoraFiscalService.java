package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SplitPaymentDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.RegraTributaria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.RegraTributariaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class CalculadoraFiscalService {

    @Autowired
    private RegraTributariaRepository regraRepository;

    // ==================================================================================
    // MÓDULO 1: CÁLCULO DE FRONTEIRA E ST (REGIME ATUAL)
    // Mantido para as operações de compra e funcionamento pré-2026
    // ==================================================================================

    private static final BigDecimal ALIQ_INTERNA_DESTINO = new BigDecimal("0.205"); // 20.5% (Ex: PE)
    private static final Set<String> ESTADOS_7_PORCENTO = Set.of("SP", "MG", "RJ", "RS", "SC", "PR");

    public BigDecimal calcularImposto(BigDecimal valorProduto, BigDecimal mvaPercentual, String ufOrigem, String ufDestino) {
        if (ufOrigem == null || ufDestino == null || ufOrigem.equalsIgnoreCase(ufDestino)) {
            return BigDecimal.ZERO;
        }

        BigDecimal aliqInterestadual = obterAliquotaInterestadual(ufOrigem, ufDestino);

        // Base ST = Valor * (1 + MVA%)
        BigDecimal mvaDecimal = mvaPercentual.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
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

    // ==================================================================================
    // MÓDULO 2: REFORMA TRIBUTÁRIA LC 214/2025 (AUTOMÁTICO)
    // Entra em vigor progressivamente conforme as datas do Banco de Dados
    // ==================================================================================

    /**
     * Calcula o Split Payment para o PDV informar ao Banco/TEF.
     * Considera a regra vigente na DATA DE HOJE.
     */
    public SplitPaymentDTO calcularSplitPayment(List<ItemVenda> itens) {
        BigDecimal totalVenda = BigDecimal.ZERO;
        BigDecimal totalImpostoRetido = BigDecimal.ZERO;

        // 1. Busca a regra vigente (2025 = Zero, 2026 = Transição, 2033 = Full)
        RegraTributaria regra = buscarRegraVigente();

        // Soma IBS + CBS para saber a alíquota cheia do período
        BigDecimal aliquotaCheiaPeriodo = regra.getAliquotaIbs().add(regra.getAliquotaCbs());

        for (ItemVenda item : itens) {
            Produto p = item.getProduto();
            BigDecimal valorItem = item.getPrecoUnitario().multiply(item.getQuantidade());
            totalVenda = totalVenda.add(valorItem);

            BigDecimal aliquotaItem = aliquotaCheiaPeriodo;

            // 2. Aplica as Reduções da LC 214/2025 baseadas na Classificação do Produto
            if (p.getClassificacaoReforma() == TipoTributacaoReforma.REDUZIDA_60) {
                // Produto tem 60% de desconto na alíquota (Paga apenas 40%)
                aliquotaItem = aliquotaCheiaPeriodo.multiply(new BigDecimal("0.40"));

            } else if (p.getClassificacaoReforma() == TipoTributacaoReforma.CESTA_BASICA ||
                    p.getClassificacaoReforma() == TipoTributacaoReforma.IMUNE) {
                // Alíquota Zero
                aliquotaItem = BigDecimal.ZERO;
            }
            // Se for PADRAO ou IMPOSTO_SELETIVO, mantém a cheia (Lógica de IS seria somada aqui)

            totalImpostoRetido = totalImpostoRetido.add(valorItem.multiply(aliquotaItem));
        }

        BigDecimal valorLiquidoLojista = totalVenda.subtract(totalImpostoRetido);

        BigDecimal aliquotaEfetiva = (totalVenda.compareTo(BigDecimal.ZERO) > 0)
                ? totalImpostoRetido.divide(totalVenda, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String mensagemStatus = (regra.getAnoReferencia() < 2026)
                ? "Regime Atual (Simples/Presumido) - Sem retenção de IBS/CBS."
                : "Regra de Transição " + regra.getAnoReferencia() + " aplicada.";

        return new SplitPaymentDTO(
                totalVenda.setScale(2, RoundingMode.HALF_UP),
                valorLiquidoLojista.setScale(2, RoundingMode.HALF_UP),
                totalImpostoRetido.setScale(2, RoundingMode.HALF_UP),
                aliquotaEfetiva,
                mensagemStatus
        );
    }

    /**
     * Simula o impacto tributário para relatórios gerenciais.
     */
    public Map<String, BigDecimal> simularTributacao2026(BigDecimal valorVenda, boolean isMonofasico) {
        // Para simulação forçamos uma data futura se quisermos ver o futuro,
        // mas aqui vamos manter coerente com a regra vigente ou pegar a de 2033 se solicitado.
        // Neste método, mantemos a lógica de buscar a regra ATUAL do sistema.
        RegraTributaria regra = buscarRegraVigente();

        Map<String, BigDecimal> simulacao = new HashMap<>();

        BigDecimal valorCBS = isMonofasico ? BigDecimal.ZERO : valorVenda.multiply(regra.getAliquotaCbs());
        BigDecimal valorIBS = valorVenda.multiply(regra.getAliquotaIbs());
        BigDecimal totalNovo = valorCBS.add(valorIBS);

        // Simula o resíduo do imposto antigo (que vai diminuindo conforme a regra)
        // Ex: Em 2029, paga 90% do ICMS antigo.
        BigDecimal impostoAntigoEstimado = valorVenda.multiply(new BigDecimal("0.18")) // 18% média
                .multiply(regra.getFatorReducaoAntigo());

        simulacao.put("CBS_VALOR", valorCBS.setScale(2, RoundingMode.HALF_UP));
        simulacao.put("IBS_VALOR", valorIBS.setScale(2, RoundingMode.HALF_UP));
        simulacao.put("IMPOSTO_ANTIGO_RESIDUAL", impostoAntigoEstimado.setScale(2, RoundingMode.HALF_UP));
        simulacao.put("CARGA_TOTAL_ESTIMADA", totalNovo.add(impostoAntigoEstimado).setScale(2, RoundingMode.HALF_UP));
        simulacao.put("ANO_REGRA_APLICADA", new BigDecimal(regra.getAnoReferencia()));

        return simulacao;
    }

    public String analisarCenarioMaisVantajoso(BigDecimal faturamentoMensal, BigDecimal comprasMensais) {
        return "Para análise detalhada, utilize o Relatório de Simulação de Impacto 2026 no Painel Fiscal.";
    }

    // --- MÉTODOS AUXILIARES ---

    private RegraTributaria buscarRegraVigente() {
        LocalDate hoje = LocalDate.now();

        return regraRepository.findRegraVigente(hoje)
                .orElseGet(() -> {
                    // FALLBACK INTELIGENTE:
                    // Se não encontrar regra (ex: estamos em 2025 e a tabela só tem 2026+),
                    // retorna uma regra "ZERADA" para o novo imposto e "CHEIA" para o antigo.
                    // Isso garante que o sistema funcione hoje sem cobrar IBS indevido.
                    return new RegraTributaria(
                            hoje.getYear(),
                            hoje,
                            hoje,
                            "0.00",  // IBS Zero
                            "0.00",  // CBS Zero
                            "1.0"    // Fator 1.0 (100% do regime antigo vigente)
                    );
                });
    }

    // Método de suporte para cálculos legados em outros serviços
    public BigDecimal calcularImpostosTotais(List<ItemVenda> itens) {
        // Cálculo simplificado (aprox 18%) para exibição rápida
        return itens.stream()
                .map(i -> i.getPrecoUnitario().multiply(i.getQuantidade()).multiply(new BigDecimal("0.18")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}