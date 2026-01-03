package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SimulacaoTributariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FiscalComplianceService {

    // Alíquota de referência padrão da Reforma (IBS+CBS estimadas em 26.5%)
    private static final BigDecimal ALIQUOTA_PADRAO_REFORMA = new BigDecimal("0.265");
    // Alíquota reduzida (60% de redução para itens essenciais/saúde/higiene pessoal)
    private static final BigDecimal ALIQUOTA_REDUZIDA_REFORMA = new BigDecimal("0.106");

    /**
     * Valida se os dados fiscais básicos estão coerentes para emissão de NF-e.
     * Isso impede o cadastro de "Produto Sujo".
     */
    public void auditarDadosFiscais(Produto produto) {
        if (produto.getNcm() == null || produto.getNcm().length() != 8) {
            throw new IllegalArgumentException("Erro Fiscal: O NCM deve conter exatamente 8 dígitos.");
        }

        if (produto.getCst() == null || produto.getCst().length() < 3) {
            throw new IllegalArgumentException("Erro Fiscal: O CST/CSOSN deve ser informado corretamente.");
        }

        // Regra de Negócio: Se é monofásico, o CST de PIS/COFINS geralmente muda
        if (produto.isMonofasico() && !produto.getCst().startsWith("04")) {
            // Nota: Lógica simplificada, num sistema real validaria tabelas complexas
            // Warning ou Log: "Atenção: Produto marcado como Monofásico mas com CST de tributado."
        }
    }

    /**
     * Gera uma simulação comparando o cenário atual com o cenário da Reforma (IVA Dual).
     */
    public SimulacaoTributariaDTO simularImpactoReforma(Produto produto) {
        BigDecimal precoVenda = produto.getPrecoVenda();
        BigDecimal custo = produto.getPrecoCusto();

        // 1. Cenário Atual (Estimativa Simplificada de 18% ICMS + 9.25% PIS/COFINS = ~27.25%)
        // Se for monofásico, o PIS/COFINS já foi pago na indústria, então a carga na venda é menor (só ICMS).
        BigDecimal aliquotaAtual = produto.isMonofasico() ? new BigDecimal("0.18") : new BigDecimal("0.2725");

        BigDecimal impostoAtual = precoVenda.multiply(aliquotaAtual);
        BigDecimal lucroAtual = precoVenda.subtract(custo).subtract(impostoAtual);

        // 2. Cenário Reforma (IVA Dual - IBS + CBS)
        // O principio é o destino e não cumulatividade plena (crédito total do custo), mas aqui focamos na venda.
        BigDecimal aliquotaReforma = ALIQUOTA_PADRAO_REFORMA;

        // Se a classificação no ENUM for algo como CESTA_BASICA ou MEDICAMENTO, aplica redução
        if (produto.getClassificacaoReforma() != null &&
                (produto.getClassificacaoReforma().name().contains("REDUZIDA") ||
                        produto.getClassificacaoReforma().name().contains("ISENTA"))) {
            aliquotaReforma = ALIQUOTA_REDUZIDA_REFORMA;
        }

        BigDecimal impostoReforma = precoVenda.multiply(aliquotaReforma);
        BigDecimal lucroReforma = precoVenda.subtract(custo).subtract(impostoReforma);

        // 3. Veredito
        String veredito;
        int comparacao = lucroReforma.compareTo(lucroAtual);
        if (comparacao > 0) veredito = "Margem Aumentará (Lucro Maior)";
        else if (comparacao < 0) veredito = "Margem Cairá (Requer Atenção)";
        else veredito = "Impacto Neutro";

        return new SimulacaoTributariaDTO(
                produto.getDescricao(),
                precoVenda,
                impostoAtual.setScale(2, RoundingMode.HALF_EVEN),
                lucroAtual.setScale(2, RoundingMode.HALF_EVEN),
                produto.getClassificacaoReforma() != null ? produto.getClassificacaoReforma().toString() : "PADRAO",
                impostoReforma.setScale(2, RoundingMode.HALF_EVEN),
                lucroReforma.setScale(2, RoundingMode.HALF_EVEN),
                veredito
        );
    }
}