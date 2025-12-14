package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

@Service
public class CalculadoraFiscalService {

    // Alíquota padrão interna de PE (Pode variar, ajustado para 2025)
    private static final BigDecimal ALIQ_INTERNA_DESTINO = new BigDecimal("0.205"); // 20.5%

    // Estados do Sul e Sudeste (Exceto ES) que pagam 7% para o Nordeste
    private static final Set<String> ESTADOS_7_PORCENTO = Set.of("SP", "MG", "RJ", "RS", "SC", "PR");

    /**
     * Calcula o imposto de fronteira (ST/DIFAL) baseado nos estados.
     */
    public BigDecimal calcularImposto(BigDecimal valorProduto, BigDecimal mvaPercentual, String ufOrigem, String ufDestino) {

        // 1. Validação de Compra Interna (Mesmo Estado)
        // Se compro de PE para vender em PE, geralmente não pago ST de entrada na barreira.
        if (ufOrigem.equalsIgnoreCase(ufDestino)) {
            return BigDecimal.ZERO;
        }

        // 2. Define Alíquota Interestadual (O que vem na Nota)
        BigDecimal aliqInterestadual = obterAliquotaInterestadual(ufOrigem, ufDestino);

        // 3. Fórmula do ICMS-ST (Substituição Tributária)
        // BaseST = Valor * (1 + MVA%)
        BigDecimal mvaDecimal = mvaPercentual.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal baseCalculoSt = valorProduto.multiply(BigDecimal.ONE.add(mvaDecimal));

        // Débito (O que o estado de destino quer receber)
        BigDecimal debitoDestino = baseCalculoSt.multiply(ALIQ_INTERNA_DESTINO);

        // Crédito (O que já foi pago na origem)
        BigDecimal creditoOrigem = valorProduto.multiply(aliqInterestadual);

        // A Pagar = Débito - Crédito
        BigDecimal valorPagar = debitoDestino.subtract(creditoOrigem);

        // Se o resultado for negativo (crédito maior que débito), imposto é zero.
        return valorPagar.max(BigDecimal.ZERO);
    }

    private BigDecimal obterAliquotaInterestadual(String origem, String destino) {
        // Regra Geral: Sul/Sudeste enviando para Nordeste = 7%
        // Demais casos (Nordeste p/ Nordeste, etc) = 12%
        // Importados (começam com 4 na nota) = 4% (Fica para v2)

        if (ESTADOS_7_PORCENTO.contains(origem.toUpperCase()) && !ESTADOS_7_PORCENTO.contains(destino.toUpperCase())) {
            return new BigDecimal("0.07"); // 7%
        }
        return new BigDecimal("0.12"); // 12%
    }
}