package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SimulacaoTributariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class FiscalComplianceService {

    private final AuditoriaService auditoriaService;

    // Alíquota de referência padrão da Reforma (IBS+CBS estimadas em 26.5%)
    private static final BigDecimal ALIQUOTA_PADRAO_REFORMA = new BigDecimal("0.265");
    // Alíquota reduzida (60% de redução para itens essenciais/saúde/higiene pessoal)
    private static final BigDecimal ALIQUOTA_REDUZIDA_REFORMA = new BigDecimal("0.106");

    // =========================================================================
    // 1. CÃO DE GUARDA DO PDV (INTELIGÊNCIA EM TEMPO REAL)
    // =========================================================================

    /**
     * Executado em milissegundos a cada item adicionado na venda do PDV.
     * Analisa e CORRIGE inconsistências antes da emissão da NFC-e.
     */
    public void aplicarComplianceNoItemVenda(ItemVenda item) {
        Produto p = item.getProduto();
        if (p == null || p.getNcm() == null) return;

        String ncm = p.getNcm().replaceAll("[^0-9]", "");

        // CFOP Padrão para Venda no Varejo PE (Dentro do Estado)
        String cfopCorreto = "5102"; // Venda normal
        String csosnCorreto = "102"; // Tributação pelo Simples
        boolean isMonofasico = false;

        // MOTOR DE REGRAS - COSMÉTICOS (SEFAZ-PE E RECEITA FEDERAL)
        boolean isCosmeticoST = ncm.startsWith("3303") || ncm.startsWith("3304") ||
                ncm.startsWith("3305") || ncm.startsWith("3307") ||
                ncm.startsWith("3401");

        if (isCosmeticoST) {
            cfopCorreto = "5405";
            csosnCorreto = "500";
            isMonofasico = true;
        } else if (ncm.startsWith("9615")) {
            cfopCorreto = "5102";
            csosnCorreto = "102";
        }

        // DETECÇÃO DE ANOMALIA E AUTOCORREÇÃO "NO VOO"
        boolean anomaliaDetectada = false;
        StringBuilder motivoAnomalia = new StringBuilder("Anomalia detectada no produto '" + p.getDescricao() + "': ");

        if (p.getCst() == null || !p.getCst().equals(csosnCorreto)) {
            motivoAnomalia.append(String.format("CSOSN incorreto (Era %s, corrigido para %s). ", p.getCst(), csosnCorreto));
            anomaliaDetectada = true;
        }

        if (p.getIsMonofasico() == null || p.getIsMonofasico() != isMonofasico) {
            motivoAnomalia.append(String.format("Status Monofásico incorreto (Ajustado para %b). ", isMonofasico));
            anomaliaDetectada = true;
        }

        // CORREÇÃO NO ITEM DA VENDA
        item.setCfop(cfopCorreto);
        item.setCsosn(csosnCorreto);
        item.setNaturezaOperacao("Venda de Mercadoria");

        if (anomaliaDetectada) {
            log.warn(motivoAnomalia.toString());
            auditoriaService.registrar(
                    "ANOMALIA_FISCAL_CORRIGIDA",
                    motivoAnomalia.toString() + " O sistema corrigiu a tributação automaticamente na NFC-e."
            );
        }
    }

    // =========================================================================
    // 2. VALIDAÇÕES E SIMULAÇÕES DA REFORMA (MÉTODOS ORIGINAIS)
    // =========================================================================

    /**
     * Valida se os dados fiscais básicos estão coerentes para emissão de NF-e.
     */
    public void auditarDadosFiscais(Produto produto) {
        if (produto.getNcm() == null || produto.getNcm().length() != 8) {
            throw new IllegalArgumentException("Erro Fiscal: O NCM deve conter exatamente 8 dígitos.");
        }

        if (produto.getCst() == null || produto.getCst().length() < 3) {
            throw new IllegalArgumentException("Erro Fiscal: O CST/CSOSN deve ser informado corretamente.");
        }

        // Regra de Negócio: Se é monofásico, o CST de PIS/COFINS geralmente muda
        if (Boolean.TRUE.equals(produto.getIsMonofasico()) && !produto.getCst().startsWith("04")) {
            // Warning ou Log
        }
    }

    /**
     * Gera uma simulação comparando o cenário atual com o cenário da Reforma (IVA Dual).
     */
    public SimulacaoTributariaDTO simularImpactoReforma(Produto produto) {
        BigDecimal precoVenda = produto.getPrecoVenda() != null ? produto.getPrecoVenda() : BigDecimal.ZERO;
        BigDecimal custo = produto.getPrecoCusto() != null ? produto.getPrecoCusto() : BigDecimal.ZERO;

        boolean monofasico = Boolean.TRUE.equals(produto.getIsMonofasico());
        BigDecimal aliquotaAtual = monofasico ? new BigDecimal("0.18") : new BigDecimal("0.2725");

        BigDecimal impostoAtual = precoVenda.multiply(aliquotaAtual);
        BigDecimal lucroAtual = precoVenda.subtract(custo).subtract(impostoAtual);

        BigDecimal aliquotaReforma = ALIQUOTA_PADRAO_REFORMA;

        if (produto.getClassificacaoReforma() != null &&
                (produto.getClassificacaoReforma().name().contains("REDUZIDA") ||
                        produto.getClassificacaoReforma().name().contains("ISENTA"))) {
            aliquotaReforma = ALIQUOTA_REDUZIDA_REFORMA;
        }

        BigDecimal impostoReforma = precoVenda.multiply(aliquotaReforma);
        BigDecimal lucroReforma = precoVenda.subtract(custo).subtract(impostoReforma);

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