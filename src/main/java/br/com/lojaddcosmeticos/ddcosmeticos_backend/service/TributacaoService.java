package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Ibpt;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.IbptRepository;
import lombok.RequiredArgsConstructor; // <--- Importante para injetar o repository
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor // Cria construtor para o IbptRepository
public class TributacaoService {

    private final IbptRepository ibptRepository;

    // Tabelas estáticas mantidas
    private static final Map<String, String> TABELA_CEST_COSMETICOS = new HashMap<>();

    // Alíquotas de Segurança (Fallback)
    private static final BigDecimal ALIQUOTA_FALLBACK_FEDERAL = new BigDecimal("15.00");
    private static final BigDecimal ALIQUOTA_FALLBACK_ESTADUAL = new BigDecimal("18.00");

    static {
        TABELA_CEST_COSMETICOS.put("33041000", "20.010.00");
        TABELA_CEST_COSMETICOS.put("33042010", "20.011.00");
        TABELA_CEST_COSMETICOS.put("34011190", "20.022.00");
    }

    /**
     * NOVO MÉTODO: Calcula impostos aproximados para o rodapé da nota (Lei 12.741)
     */
    public String calcularTextoTransparencia(Venda venda) {
        BigDecimal totalFederal = BigDecimal.ZERO;
        BigDecimal totalEstadual = BigDecimal.ZERO;
        boolean usouEstimativa = false;

        if (venda.getItens() == null) return "";

        for (ItemVenda item : venda.getItens()) {
            Produto produto = item.getProduto();
            if (produto == null) continue;

            BigDecimal totalItem = item.getPrecoUnitario().multiply(item.getQuantidade());
            String ncmLimpo = (produto.getNcm() != null) ? produto.getNcm().replaceAll("[^0-9]", "") : "";

            Optional<Ibpt> ibptOpt = ibptRepository.findByNcm(ncmLimpo);

            if (ibptOpt.isPresent()) {
                // Cálculo Real via Tabela IBPT
                Ibpt ibpt = ibptOpt.get();
                boolean isImportado = isProdutoImportado(produto.getOrigem());
                BigDecimal aliqFed = isImportado ? ibpt.getImportado() : ibpt.getNacional();

                if (aliqFed != null) totalFederal = totalFederal.add(calcImposto(totalItem, aliqFed));
                if (ibpt.getEstadual() != null) totalEstadual = totalEstadual.add(calcImposto(totalItem, ibpt.getEstadual()));
            } else {
                // Fallback se não achar NCM
                usouEstimativa = true;
                totalFederal = totalFederal.add(calcImposto(totalItem, ALIQUOTA_FALLBACK_FEDERAL));
                totalEstadual = totalEstadual.add(calcImposto(totalItem, ALIQUOTA_FALLBACK_ESTADUAL));
            }
        }

        String fonte = usouEstimativa ? "Estimativa/IBPT" : "IBPT";
        return String.format("Trib aprox R$ %.2f Fed, R$ %.2f Est. Fonte: %s",
                totalFederal, totalEstadual, fonte);
    }

    // --- Métodos Auxiliares ---

    private BigDecimal calcImposto(BigDecimal base, BigDecimal aliquota) {
        if (base == null || aliquota == null) return BigDecimal.ZERO;
        return base.multiply(aliquota).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private boolean isProdutoImportado(String origem) {
        if (origem == null) return false;
        return origem.equals("1") || origem.equals("2") || origem.equals("6") || origem.equals("7");
    }

    // --- Métodos Originais Mantidos ---

    public Map<String, String> consultarRegrasPorNcm(String ncm) {
        String ncmLimpo = ncm.replaceAll("[^0-9]", "");
        Map<String, String> resultado = new HashMap<>();
        if (TABELA_CEST_COSMETICOS.containsKey(ncmLimpo)) {
            resultado.put("cest", TABELA_CEST_COSMETICOS.get(ncmLimpo));
        }
        boolean ehMonofasico = ncmLimpo.startsWith("3303") || ncmLimpo.startsWith("3304");
        resultado.put("monofasico", String.valueOf(ehMonofasico));
        return resultado;
    }

    @Transactional
    public void classificarProduto(Produto produto) {
        if (produto.getNcm() == null || produto.getNcm().isBlank()) return;

        String ncmLimpo = produto.getNcm().replaceAll("[^0-9]", "");

        if (produto.getCest() == null || produto.getCest().isBlank()) {
            if (TABELA_CEST_COSMETICOS.containsKey(ncmLimpo)) {
                produto.setCest(TABELA_CEST_COSMETICOS.get(ncmLimpo));
            }
        }

        boolean ehMonofasico = ncmLimpo.startsWith("3303") || ncmLimpo.startsWith("3304") ||
                ncmLimpo.startsWith("3305") || ncmLimpo.startsWith("3307") || ncmLimpo.startsWith("3401");

        log.info("Produto {} classificado: NCM {}, Monofásico: {}", produto.getDescricao(), ncmLimpo, ehMonofasico);
    }
}