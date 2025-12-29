package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CalculadoraFiscalService {

    // ==================================================================================
    // MÓDULO 1: CÁLCULO DE FRONTEIRA E ST (ATUAL - USADO EM COMPRAS)
    // ==================================================================================

    // Alíquota padrão interna de PE (Ajustado)
    private static final BigDecimal ALIQ_INTERNA_DESTINO = new BigDecimal("0.205"); // 20.5%
    // Estados do Sul e Sudeste (Exceto ES) que pagam 7% para o Nordeste
    private static final Set<String> ESTADOS_7_PORCENTO = Set.of("SP", "MG", "RJ", "RS", "SC", "PR");

    /**
     * Método restaurado para corrigir o erro na linha 53 do PedidoCompraService
     */
    public BigDecimal calcularImposto(BigDecimal valorProduto, BigDecimal mvaPercentual, String ufOrigem, String ufDestino) {
        if (ufOrigem == null || ufDestino == null || ufOrigem.equalsIgnoreCase(ufDestino)) {
            return BigDecimal.ZERO;
        }

        BigDecimal aliqInterestadual = obterAliquotaInterestadual(ufOrigem, ufDestino);
        BigDecimal mvaDecimal = mvaPercentual.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal baseCalculoSt = valorProduto.multiply(BigDecimal.ONE.add(mvaDecimal));

        BigDecimal debitoDestino = baseCalculoSt.multiply(ALIQ_INTERNA_DESTINO);
        BigDecimal creditoOrigem = valorProduto.multiply(aliqInterestadual);

        BigDecimal valorPagar = debitoDestino.subtract(creditoOrigem);
        return valorPagar.max(BigDecimal.ZERO);
    }

    private BigDecimal obterAliquotaInterestadual(String origem, String destino) {
        if (ESTADOS_7_PORCENTO.contains(origem.toUpperCase()) && !ESTADOS_7_PORCENTO.contains(destino.toUpperCase())) {
            return new BigDecimal("0.07");
        }
        return new BigDecimal("0.12");
    }

    // ==================================================================================
    // MÓDULO 2: SIMULAÇÃO REFORMA TRIBUTÁRIA 2026 (NOVO)
    // ==================================================================================

    private static final BigDecimal ALIQUOTA_PADRAO_IBS = new BigDecimal("0.17");
    private static final BigDecimal ALIQUOTA_PADRAO_CBS = new BigDecimal("0.09");
    private static final BigDecimal ALIQUOTA_SIMPLES_COMERCIO = new BigDecimal("0.04");

    public BigDecimal calcularImpostosTotais(List<ItemVenda> itens) {
        return itens.stream()
                .map(this::calcularImpostoItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcularImpostoItem(ItemVenda item) {
        if (item.getProduto() == null) return BigDecimal.ZERO;
        BigDecimal valorTotal = item.getPrecoUnitario().multiply(item.getQuantidade());
        // Simulação genérica de carga tributária atual (aprox 18%)
        return valorTotal.multiply(new BigDecimal("0.18"));
    }

    public Map<String, BigDecimal> simularTributacao2026(BigDecimal valorVenda, boolean isMonofasico) {
        Map<String, BigDecimal> simulacao = new HashMap<>();
        BigDecimal baseCalculo = valorVenda;

        BigDecimal valorCBS = isMonofasico ? BigDecimal.ZERO : baseCalculo.multiply(ALIQUOTA_PADRAO_CBS);
        BigDecimal valorIBS = baseCalculo.multiply(ALIQUOTA_PADRAO_IBS);
        BigDecimal totalNovo = valorCBS.add(valorIBS);

        simulacao.put("CBS_ESTIMADO", valorCBS.setScale(2, RoundingMode.HALF_UP));
        simulacao.put("IBS_ESTIMADO", valorIBS.setScale(2, RoundingMode.HALF_UP));
        simulacao.put("TOTAL_IVA_DUAL", totalNovo.setScale(2, RoundingMode.HALF_UP));

        return simulacao;
    }

    public String analisarCenarioMaisVantajoso(BigDecimal faturamentoMensal, BigDecimal comprasMensais) {
        BigDecimal impostoAtual = faturamentoMensal.multiply(ALIQUOTA_SIMPLES_COMERCIO);

        BigDecimal aliquotaTotalIva = ALIQUOTA_PADRAO_CBS.add(ALIQUOTA_PADRAO_IBS);
        BigDecimal ivaVendas = faturamentoMensal.multiply(aliquotaTotalIva);
        BigDecimal creditoCompras = comprasMensais.multiply(aliquotaTotalIva);
        BigDecimal impostoFuturo = ivaVendas.subtract(creditoCompras).max(BigDecimal.ZERO);

        StringBuilder relatorio = new StringBuilder();
        relatorio.append("--- Análise Tributária Preliminar ---\n");
        relatorio.append(String.format("Imposto Estimado Atual (Simples): R$ %.2f\n", impostoAtual));
        relatorio.append(String.format("Imposto Estimado 2026 (IVA): R$ %.2f\n", impostoFuturo));

        if (impostoAtual.compareTo(impostoFuturo) < 0) {
            relatorio.append("CONCLUSÃO: O regime ATUAL (Simples) parece mais vantajoso.");
        } else {
            relatorio.append("CONCLUSÃO: A migração para o IVA pode gerar economia pelos créditos.");
        }
        return relatorio.toString();
    }
}