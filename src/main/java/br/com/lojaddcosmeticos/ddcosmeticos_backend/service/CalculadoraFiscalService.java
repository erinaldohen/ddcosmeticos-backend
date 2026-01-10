package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoExternoDTO;
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
    // NOVO MÓDULO: INTELIGÊNCIA FISCAL (CLASSIFICAÇÃO AUTOMÁTICA)
    // ==================================================================================

    /**
     * Aplica regras fiscais em uma entidade Produto do banco.
     * Retorna TRUE se houve alteração (para o saneamento saber se precisa salvar).
     */
    public boolean aplicarRegrasNoProduto(Produto p) {
        if (p.getNcm() == null || p.getNcm().isEmpty()) return false;

        RegraFiscalResultado resultado = calcularRegras(p.getNcm());
        boolean alterou = false;

        if (p.isMonofasico() != resultado.monofasico) {
            p.setMonofasico(resultado.monofasico);
            alterou = true;
        }
        if (p.getCst() == null || !p.getCst().equals(resultado.cst)) {
            p.setCst(resultado.cst);
            alterou = true;
        }
        if (p.getClassificacaoReforma() != resultado.classificacaoReforma) {
            p.setClassificacaoReforma(resultado.classificacaoReforma);
            alterou = true;
        }
        return alterou;
    }

    /**
     * Aplica regras fiscais no DTO que vem da API Externa.
     */
    public void aplicarRegras(ProdutoExternoDTO dto) {
        if (dto.getNcm() == null || dto.getNcm().isEmpty()) {
            dto.setMonofasico(false);
            dto.setCst("102");
            dto.setClassificacaoReforma("PADRAO");
            return;
        }
        RegraFiscalResultado resultado = calcularRegras(dto.getNcm());
        dto.setMonofasico(resultado.monofasico);
        dto.setCst(resultado.cst);
        dto.setClassificacaoReforma(resultado.classificacaoReforma.name());
    }

    // Lógica baseada no NCM (Lei 10.147/2000 e LC 214/2025)
    private RegraFiscalResultado calcularRegras(String ncm) {
        String ncmLimpo = ncm.replace(".", "").trim();

        // 1. Monofásico (Cosméticos e Higiene Pessoal)
        boolean isMonofasico =
                ncmLimpo.startsWith("3303") ||
                        ncmLimpo.startsWith("3304") ||
                        ncmLimpo.startsWith("3305") ||
                        ncmLimpo.startsWith("3307") ||
                        ncmLimpo.startsWith("3401") ||
                        ncmLimpo.startsWith("9619");

        // 2. CST / CSOSN (Simples Nacional)
        // 500 = ICMS cobrado anteriormente por ST ou antecipação (Monofásico)
        // 102 = Tributado pelo Simples
        String cst = isMonofasico ? "500" : "102";

        // 3. Reforma Tributária
        TipoTributacaoReforma classificacao;
        if (ncmLimpo.startsWith("9619")) {
            classificacao = TipoTributacaoReforma.CESTA_BASICA;
        } else if (ncmLimpo.startsWith("3306") || ncmLimpo.startsWith("3401")) {
            classificacao = TipoTributacaoReforma.REDUZIDA_60;
        } else {
            classificacao = TipoTributacaoReforma.PADRAO;
        }

        return new RegraFiscalResultado(isMonofasico, cst, classificacao);
    }

    private record RegraFiscalResultado(boolean monofasico, String cst, TipoTributacaoReforma classificacaoReforma) {}

    // ==================================================================================
    // MÓDULO 1 E 2: LÓGICA EXISTENTE (MANTIDA INTEGRALMENTE)
    // ==================================================================================

    private static final BigDecimal ALIQ_INTERNA_DESTINO = new BigDecimal("0.205");
    private static final Set<String> ESTADOS_7_PORCENTO = Set.of("SP", "MG", "RJ", "RS", "SC", "PR");

    public BigDecimal calcularImposto(BigDecimal valorProduto, BigDecimal mvaPercentual, String ufOrigem, String ufDestino) {
        if (ufOrigem == null || ufDestino == null || ufOrigem.equalsIgnoreCase(ufDestino)) {
            return BigDecimal.ZERO;
        }
        BigDecimal aliqInterestadual = obterAliquotaInterestadual(ufOrigem, ufDestino);
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

    public SplitPaymentDTO calcularSplitPayment(List<ItemVenda> itens) {
        BigDecimal totalVenda = BigDecimal.ZERO;
        BigDecimal totalImpostoRetido = BigDecimal.ZERO;
        RegraTributaria regra = buscarRegraVigente();
        BigDecimal aliquotaCheiaPeriodo = regra.getAliquotaIbs().add(regra.getAliquotaCbs());

        for (ItemVenda item : itens) {
            Produto p = item.getProduto();
            BigDecimal valorItem = item.getPrecoUnitario().multiply(item.getQuantidade());
            totalVenda = totalVenda.add(valorItem);
            BigDecimal aliquotaItem = aliquotaCheiaPeriodo;

            if (p.getClassificacaoReforma() == TipoTributacaoReforma.REDUZIDA_60) {
                aliquotaItem = aliquotaCheiaPeriodo.multiply(new BigDecimal("0.40"));
            } else if (p.getClassificacaoReforma() == TipoTributacaoReforma.CESTA_BASICA ||
                    p.getClassificacaoReforma() == TipoTributacaoReforma.IMUNE) {
                aliquotaItem = BigDecimal.ZERO;
            }
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

    public Map<String, BigDecimal> simularTributacao2026(BigDecimal valorVenda, boolean isMonofasico) {
        RegraTributaria regra = buscarRegraVigente();
        Map<String, BigDecimal> simulacao = new HashMap<>();
        BigDecimal valorCBS = isMonofasico ? BigDecimal.ZERO : valorVenda.multiply(regra.getAliquotaCbs());
        BigDecimal valorIBS = valorVenda.multiply(regra.getAliquotaIbs());
        BigDecimal totalNovo = valorCBS.add(valorIBS);
        BigDecimal impostoAntigoEstimado = valorVenda.multiply(new BigDecimal("0.18"))
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

    private RegraTributaria buscarRegraVigente() {
        LocalDate hoje = LocalDate.now();
        return regraRepository.findRegraVigente(hoje)
                .orElseGet(() -> new RegraTributaria(
                        hoje.getYear(), hoje, hoje, "0.00", "0.00", "1.0"
                ));
    }

    public BigDecimal calcularImpostosTotais(List<ItemVenda> itens) {
        return itens.stream()
                .map(i -> i.getPrecoUnitario().multiply(i.getQuantidade()).multiply(new BigDecimal("0.18")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}