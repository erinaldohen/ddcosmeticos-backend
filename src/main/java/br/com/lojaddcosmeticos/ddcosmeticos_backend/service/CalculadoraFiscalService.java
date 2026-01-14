package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoExternoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoFiscalCarrinhoDTO;
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
    // MÓDULO: INTELIGÊNCIA FISCAL (CLASSIFICAÇÃO AUTOMÁTICA ATUALIZADA)
    // ==================================================================================

    public boolean aplicarRegrasFiscais(Produto p) {
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
        try {
            if (p.isImpostoSeletivo() != resultado.impostoSeletivo) {
                p.setImpostoSeletivo(resultado.impostoSeletivo);
                alterou = true;
            }
        } catch (Exception ignored) {}

        return alterou;
    }

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

    private RegraFiscalResultado calcularRegras(String ncm) {
        String ncmLimpo = ncm.replace(".", "").trim();

        boolean isMonofasico =
                ncmLimpo.startsWith("3303") ||
                        ncmLimpo.startsWith("3304") ||
                        ncmLimpo.startsWith("3305") ||
                        ncmLimpo.startsWith("3307") ||
                        ncmLimpo.startsWith("3401") ||
                        ncmLimpo.startsWith("9619");

        String cst = isMonofasico ? "500" : "102";

        TipoTributacaoReforma classificacao;
        boolean impostoSeletivo = false;

        if (ncmLimpo.startsWith("9619") || ncmLimpo.startsWith("0201") ||
                ncmLimpo.startsWith("1006") || ncmLimpo.startsWith("0713")) {
            classificacao = TipoTributacaoReforma.CESTA_BASICA;
        } else if (ncmLimpo.startsWith("3306") || ncmLimpo.startsWith("3401")) {
            classificacao = TipoTributacaoReforma.REDUZIDA_60;
        } else if (ncmLimpo.startsWith("2203") || ncmLimpo.startsWith("2204") || ncmLimpo.startsWith("2402")) {
            classificacao = TipoTributacaoReforma.IMPOSTO_SELETIVO;
            impostoSeletivo = true;
        } else {
            classificacao = TipoTributacaoReforma.PADRAO;
        }

        return new RegraFiscalResultado(isMonofasico, cst, classificacao, impostoSeletivo);
    }

    private record RegraFiscalResultado(
            boolean monofasico,
            String cst,
            TipoTributacaoReforma classificacaoReforma,
            boolean impostoSeletivo
    ) {}

    // ==================================================================================
    // LÓGICA EXISTENTE: MANTIDA E CORRIGIDA
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
            // CORREÇÃO: Conversão segura de Quantidade (Integer -> BigDecimal)
            BigDecimal qtd = new BigDecimal(item.getQuantidade().toString());
            BigDecimal valorItem = item.getPrecoUnitario().multiply(qtd);

            totalVenda = totalVenda.add(valorItem);

            BigDecimal aliquotaItem = aliquotaCheiaPeriodo;

            if (p.getClassificacaoReforma() == TipoTributacaoReforma.REDUZIDA_60) {
                aliquotaItem = aliquotaCheiaPeriodo.multiply(new BigDecimal("0.40"));
            } else if (p.getClassificacaoReforma() == TipoTributacaoReforma.CESTA_BASICA ||
                    p.getClassificacaoReforma() == TipoTributacaoReforma.IMUNE) {
                aliquotaItem = BigDecimal.ZERO;
            } else if (p.getClassificacaoReforma() == TipoTributacaoReforma.IMPOSTO_SELETIVO) {
                aliquotaItem = aliquotaCheiaPeriodo.add(new BigDecimal("0.15"));
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
                        hoje.getYear(), hoje, hoje, "0.175", "0.090", "1.0"
                ));
    }

    public BigDecimal calcularImpostosTotais(List<ItemVenda> itens) {
        return itens.stream()
                .map(i -> {
                    // CORREÇÃO: Conversão segura
                    BigDecimal qtd = new BigDecimal(i.getQuantidade().toString());
                    return i.getPrecoUnitario().multiply(qtd).multiply(new BigDecimal("0.18"));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --- CORREÇÃO DA LINHA 243 E ATUALIZAÇÃO DA LÓGICA DE CÁLCULO ---
    public ResumoFiscalCarrinhoDTO calcularTotaisCarrinho(List<ItemVenda> itens) {
        BigDecimal totalVenda = BigDecimal.ZERO;
        BigDecimal somaIbs = BigDecimal.ZERO;
        BigDecimal somaCbs = BigDecimal.ZERO;
        BigDecimal somaIs = BigDecimal.ZERO;

        for (ItemVenda item : itens) {
            Produto p = item.getProduto();
            // CORREÇÃO: Conversão de Integer/Double para BigDecimal de forma segura
            BigDecimal qtd = (item.getQuantidade() != null)
                    ? new BigDecimal(item.getQuantidade().toString())
                    : BigDecimal.ZERO;

            BigDecimal preco = (item.getPrecoUnitario() != null)
                    ? item.getPrecoUnitario()
                    : BigDecimal.ZERO;

            BigDecimal subtotal = preco.multiply(qtd);

            // Alíquotas Padrão LC 214 (Estimativa Geral)
            BigDecimal aliqIbs = new BigDecimal("17.5"); // IBS
            BigDecimal aliqCbs = new BigDecimal("9.0");  // CBS
            BigDecimal aliqIs = BigDecimal.ZERO;

            // Lógica de Inteligência Fiscal (Aplica as reduções/acréscimos)
            if (p.getClassificacaoReforma() != null) {
                if (p.getClassificacaoReforma() == TipoTributacaoReforma.IMPOSTO_SELETIVO) {
                    aliqIs = new BigDecimal("10.0"); // Exemplo de seletivo
                } else if (p.getClassificacaoReforma() == TipoTributacaoReforma.REDUZIDA_60) {
                    aliqIbs = aliqIbs.multiply(new BigDecimal("0.4"));
                    aliqCbs = aliqCbs.multiply(new BigDecimal("0.4"));
                } else if (p.getClassificacaoReforma() == TipoTributacaoReforma.CESTA_BASICA ||
                        p.getClassificacaoReforma() == TipoTributacaoReforma.IMUNE) {
                    aliqIbs = BigDecimal.ZERO;
                    aliqCbs = BigDecimal.ZERO;
                }
            }

            // Cálculos item a item
            BigDecimal valorIbs = subtotal.multiply(aliqIbs).divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
            BigDecimal valorCbs = subtotal.multiply(aliqCbs).divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);
            BigDecimal valorIs  = subtotal.multiply(aliqIs).divide(new BigDecimal("100"), 2, RoundingMode.HALF_EVEN);

            // Acumuladores
            totalVenda = totalVenda.add(subtotal);
            somaIbs = somaIbs.add(valorIbs);
            somaCbs = somaCbs.add(valorCbs);
            somaIs = somaIs.add(valorIs);
        }

        BigDecimal totalImpostos = somaIbs.add(somaCbs).add(somaIs);
        BigDecimal totalLiquido = totalVenda.subtract(totalImpostos);

        BigDecimal aliquotaEfetiva = (totalVenda.compareTo(BigDecimal.ZERO) > 0)
                ? totalImpostos.divide(totalVenda, 4, RoundingMode.HALF_EVEN).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        return new ResumoFiscalCarrinhoDTO(
                totalVenda,
                somaIbs,
                somaCbs,
                somaIs,
                totalLiquido,
                aliquotaEfetiva
        );
    }
}