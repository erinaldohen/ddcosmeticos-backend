package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Ibpt;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.IbptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TributosService {

    private final IbptRepository ibptRepository;

    private static final BigDecimal ALIQUOTA_FALLBACK_FEDERAL = new BigDecimal("15.00");
    private static final BigDecimal ALIQUOTA_FALLBACK_ESTADUAL = new BigDecimal("18.00");

    public String calcularTextoTransparencia(Venda venda) {
        BigDecimal totalFederal = BigDecimal.ZERO;
        BigDecimal totalEstadual = BigDecimal.ZERO;
        boolean usouEstimativa = false;

        for (ItemVenda item : venda.getItens()) {
            Produto produto = item.getProduto();

            if (produto == null) continue;

            BigDecimal valorTotalItem = item.getPrecoUnitario().multiply(item.getQuantidade());

            String ncmLimpo = (produto.getNcm() != null) ? produto.getNcm().replaceAll("[^0-9]", "") : "";

            Optional<Ibpt> ibptOpt = ibptRepository.findByNcm(ncmLimpo);

            if (ibptOpt.isPresent()) {
                Ibpt ibpt = ibptOpt.get();
                boolean isImportado = isProdutoImportado(produto.getOrigem());

                BigDecimal aliqFed = isImportado ? ibpt.getImportado() : ibpt.getNacional();
                BigDecimal aliqEst = ibpt.getEstadual();

                if (aliqFed != null) {
                    totalFederal = totalFederal.add(calcularValorImposto(valorTotalItem, aliqFed));
                }
                if (aliqEst != null) {
                    totalEstadual = totalEstadual.add(calcularValorImposto(valorTotalItem, aliqEst));
                }

            } else {
                usouEstimativa = true;
                log.warn("⚠️ TributacaoService: NCM '{}' não encontrado para o produto '{}'. Usando alíquota média.",
                        ncmLimpo, produto.getDescricao());

                totalFederal = totalFederal.add(calcularValorImposto(valorTotalItem, ALIQUOTA_FALLBACK_FEDERAL));
                totalEstadual = totalEstadual.add(calcularValorImposto(valorTotalItem, ALIQUOTA_FALLBACK_ESTADUAL));
            }
        }

        String fonte = usouEstimativa ? "IBPT/Estimativa" : "IBPT";

        return String.format("Trib aprox R$ %.2f Fed, R$ %.2f Est. Fonte: %s",
                totalFederal, totalEstadual, fonte);
    }

    private BigDecimal calcularValorImposto(BigDecimal valorBase, BigDecimal aliquota) {
        if (valorBase == null || aliquota == null) return BigDecimal.ZERO;
        return valorBase.multiply(aliquota).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private boolean isProdutoImportado(String origem) {
        if (origem == null) return false;
        return origem.equals("1") || origem.equals("2") || origem.equals("6") || origem.equals("7");
    }
}