package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.CrossSellDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ProdutoRepository produtoRepository;
    private final VendaRepository vendaRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final VendaPerdidaRepository vendaPerdidaRepository;
    private final ConfiguracaoLojaRepository configuracaoRepository;
    private final PrecificacaoService precificacaoService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public Map<String, Object> obterDadosDoDashboard(String periodo) {
        LocalDateTime agora = LocalDateTime.now();

        // =========================================================================
        // MOTOR DE FILTRO TEMPORAL BLINDADO
        // Define o início e fim baseando-se no que o utilizador pediu no Frontend
        // =========================================================================
        LocalDateTime inicioRef = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimRef = agora.toLocalDate().withDayOfMonth(agora.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);

        if ("hoje".equalsIgnoreCase(periodo)) {
            inicioRef = agora.toLocalDate().atStartOfDay();
            fimRef = agora.toLocalDate().atTime(LocalTime.MAX);
        } else if ("mes_passado".equalsIgnoreCase(periodo)) {
            inicioRef = agora.minusMonths(1).toLocalDate().withDayOfMonth(1).atStartOfDay();
            fimRef = agora.minusMonths(1).toLocalDate().withDayOfMonth(agora.minusMonths(1).toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);
        }

        // =========================================================================
        // CONFIGURAÇÕES GLOBAIS (META MENSAL)
        // =========================================================================
        ConfiguracaoLoja config = configuracaoRepository.findAll().stream().findFirst().orElse(null);
        BigDecimal metaMensal = BigDecimal.valueOf(10000.00);

        if (config != null && config.getVendas() != null) {
            BigDecimal metaSalva = config.getVendas().getMetaMensal();
            if (metaSalva != null && metaSalva.compareTo(BigDecimal.ZERO) > 0) {
                metaMensal = metaSalva;
            }
        }

        // =========================================================================
        // DADOS REAIS BASEADOS NO PERÍODO SELECIONADO
        // A lista vendasDoPeriodo vai alimentar TODOS os cards
        // =========================================================================
        List<Venda> vendasDoPeriodo = vendaRepository.buscarVendasPorPeriodo(inicioRef, fimRef);

        BigDecimal faturamentoPeriodo = BigDecimal.ZERO;
        BigDecimal custoTotalPeriodo = BigDecimal.ZERO;
        BigDecimal descontosPeriodo = BigDecimal.ZERO;
        long totalLinhasItensPeriodo = 0L;

        Map<String, BigDecimal> faturamentoPorForma = new HashMap<>();
        Map<String, BigDecimal> faturamentoPorCategoria = new HashMap<>();
        Map<Integer, List<Venda>> agrupadoPorHora = new HashMap<>();
        Map<String, BigDecimal> mapaDias = new TreeMap<>();
        DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("dd/MM");

        for (Venda v : vendasDoPeriodo) {
            BigDecimal totalVenda = safeBigDecimal(v.getValorTotal());
            faturamentoPeriodo = faturamentoPeriodo.add(totalVenda);
            descontosPeriodo = descontosPeriodo.add(safeBigDecimal(v.getDescontoTotal()));

            // Agrupamento para os Gráficos
            agrupadoPorHora.computeIfAbsent(v.getDataVenda().getHour(), k -> new ArrayList<>()).add(v);
            mapaDias.merge(v.getDataVenda().format(fmtDia), totalVenda, BigDecimal::add);

            if (v.getPagamentos() != null) {
                v.getPagamentos().forEach(p -> {
                    String forma = p.getFormaPagamento().name().replace("_", " ");
                    faturamentoPorForma.merge(forma, safeBigDecimal(p.getValor()), BigDecimal::add);
                });
            }

            if (v.getItens() != null) {
                totalLinhasItensPeriodo += v.getItens().size();
                for (ItemVenda i : v.getItens()) {
                    BigDecimal qtd = i.getQuantidade() != null ? i.getQuantidade() : BigDecimal.ZERO;
                    BigDecimal custoUnit = safeBigDecimal(i.getCustoUnitarioHistorico());
                    if (custoUnit.compareTo(BigDecimal.ZERO) == 0 && i.getProduto() != null) {
                        custoUnit = safeBigDecimal(i.getProduto().getPrecoCusto());
                    }
                    custoTotalPeriodo = custoTotalPeriodo.add(custoUnit.multiply(qtd));

                    String cat = (i.getProduto() != null && i.getProduto().getCategoria() != null)
                            ? i.getProduto().getCategoria() : "Diversos";
                    faturamentoPorCategoria.merge(cat, safeBigDecimal(i.getPrecoUnitario()).multiply(qtd), BigDecimal::add);
                }
            }
        }

        // --- Gráficos do Frontend ---
        List<Map<String, Object>> formasPagamentoList = faturamentoPorForma.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    return m; // Cores geridas no Front
                }).collect(Collectors.toList());

        List<Map<String, Object>> topCategorias = faturamentoPorCategoria.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("nome", e.getKey());
                    m.put("valor", e.getValue());
                    return m;
                }).collect(Collectors.toList());

        long quantidadeVendas = vendasDoPeriodo.size();
        BigDecimal ticketMedio = quantidadeVendas > 0 ? faturamentoPeriodo.divide(new BigDecimal(quantidadeVendas), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        double mixProdutos = quantidadeVendas > 0 ? (double) totalLinhasItensPeriodo / quantidadeVendas : 0.0;

        List<Map<String, Object>> vendasPorHora = new ArrayList<>();
        for (int h = 8; h <= 20; h++) {
            Map<String, Object> m = new HashMap<>();
            m.put("horaStr", String.format("%02dh", h));
            m.put("qtd", agrupadoPorHora.getOrDefault(h, new ArrayList<>()).size());
            vendasPorHora.add(m);
        }

        List<Map<String, Object>> graficoVendas = mapaDias.entrySet().stream().map(e -> {
            Map<String, Object> m = new HashMap<>(); m.put("data", e.getKey()); m.put("total", e.getValue()); return m;
        }).collect(Collectors.toList());

        BigDecimal impostoMes = faturamentoPeriodo.multiply(new BigDecimal("0.04"));

        List<Map<String, Object>> origemVendas = vendaRepository.somarFaturamentoPorOrigemMes(inicioRef).stream()
                .map(proj -> {
                    Map<String, Object> m = new HashMap<>();
                    String nomeCanal = proj.getCanalOrigem() != null ? proj.getCanalOrigem().getDescricao() : "Loja Física";
                    m.put("name", nomeCanal);
                    m.put("value", proj.getTotal());
                    return m;
                }).collect(Collectors.toList());

        // --- Estoque e Inteligência ---
        ProdutoRepository.RiscoEstoqueProjection projVencendo = produtoRepository.calcularRiscoVencimento(LocalDate.now().plusDays(60));
        Map<String, Object> riscoVencendo = new HashMap<>();
        riscoVencendo.put("itens", projVencendo != null && projVencendo.getItens() != null ? projVencendo.getItens() : 0);
        riscoVencendo.put("valorRisco", projVencendo != null && projVencendo.getValorRisco() != null ? projVencendo.getValorRisco() : BigDecimal.ZERO);

        ProdutoRepository.RiscoEstoqueProjection projCurvaC = produtoRepository.calcularEstoqueParado(LocalDate.now().minusDays(90));
        Map<String, Object> riscoCurvaC = new HashMap<>();
        riscoCurvaC.put("itens", projCurvaC != null && projCurvaC.getItens() != null ? projCurvaC.getItens() : 0);
        riscoCurvaC.put("valorImobilizado", projCurvaC != null && projCurvaC.getValorRisco() != null ? projCurvaC.getValorRisco() : BigDecimal.ZERO);

        List<CrossSellDTO> afinidadeProdutos = vendaRepository.buscarCrossSell(inicioRef, fimRef, PageRequest.of(0, 3));
        Map<String, Object> inteligenciaVendas = new HashMap<>();
        inteligenciaVendas.put("afinidade", afinidadeProdutos);

        Long totalTicketsID = vendaRepository.contarVendasIdentificadasNoMes(inicioRef);
        Long ticketsRecorrentes = vendaRepository.contarVendasRecorrentesNoMes(inicioRef);
        double taxaRecorrencia = (totalTicketsID != null && totalTicketsID > 0)
                ? ((double) (ticketsRecorrentes != null ? ticketsRecorrentes : 0) / totalTicketsID) * 100
                : 0.0;

        // =========================================================================
        // O NOVO CÁLCULO REAL DE FLUXO DE 7 DIAS (A partir de Hoje, não do filtro)
        // =========================================================================
        int diasPassados = Math.max(1, ("hoje".equals(periodo) ? 1 : agora.getDayOfMonth()));
        int diasNoMes = YearMonth.from(inicioRef).lengthOfMonth();
        BigDecimal runRate = faturamentoPeriodo.divide(new BigDecimal(diasPassados), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(diasNoMes));

        LocalDate diaAtual = agora.toLocalDate();
        LocalDate daquiA7Dias = diaAtual.plusDays(7);

        // A Receber: Boletos de Clientes (Crediário)
        BigDecimal totalReceitasPrevistas = BigDecimal.ZERO;
        try {
            totalReceitasPrevistas = contaReceberRepository.findAll().stream()
                    .filter(c -> !c.isPago() && c.getDataVencimento() != null &&
                            !c.getDataVencimento().isBefore(diaAtual) && !c.getDataVencimento().isAfter(daquiA7Dias))
                    .map(c -> safeBigDecimal(c.getValor()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.warn("Repositório ContaReceber vazio ou sem dados pendentes.");
        }

        // A Pagar: Faturas de Fornecedores, Despesas e Boletos
        BigDecimal totalDespesasPrevistas = BigDecimal.ZERO;
        try {
            totalDespesasPrevistas = contaPagarRepository.findAll().stream()
                    .filter(c -> !c.isPago() && c.getDataVencimento() != null &&
                            !c.getDataVencimento().isBefore(diaAtual) && !c.getDataVencimento().isAfter(daquiA7Dias))
                    .map(c -> safeBigDecimal(c.getValorTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (Exception e) {
            log.warn("Repositório ContaPagar vazio ou sem dados pendentes.");
        }

        Map<String, Object> fluxo7Dias = new HashMap<>();
        fluxo7Dias.put("receitasPrevistas", totalReceitasPrevistas);
        fluxo7Dias.put("despesasPrevistas", totalDespesasPrevistas);
        fluxo7Dias.put("saldoProjetado", totalReceitasPrevistas.subtract(totalDespesasPrevistas));

        // Vendas Perdidas
        Long qtdVendasPerdidas = vendaPerdidaRepository.contarRupturasNoPeriodo(inicioRef, fimRef);
        Map<String, Object> vendasPerdidas = new HashMap<>();
        vendasPerdidas.put("quantidade", qtdVendasPerdidas != null ? qtdVendasPerdidas : 0);
        vendasPerdidas.put("valorEstimado", ticketMedio.multiply(new BigDecimal(qtdVendasPerdidas != null ? qtdVendasPerdidas : 0)));

        // Crescimento MoM
        LocalDateTime inicioMesAnterior = inicioRef.minusMonths(1);
        LocalDateTime fimMesAnteriorAgra = fimRef.minusMonths(1);
        BigDecimal faturamentoMesAnteriorParcial = vendaRepository.somarFaturamento(inicioMesAnterior, fimMesAnteriorAgra);
        if (faturamentoMesAnteriorParcial == null) faturamentoMesAnteriorParcial = BigDecimal.ZERO;

        double crescimentoMoM = 0.0;
        if (faturamentoMesAnteriorParcial.compareTo(BigDecimal.ZERO) > 0) {
            crescimentoMoM = faturamentoPeriodo.subtract(faturamentoMesAnteriorParcial).divide(faturamentoMesAnteriorParcial, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();
        } else if (faturamentoPeriodo.compareTo(BigDecimal.ZERO) > 0) {
            crescimentoMoM = 100.0;
        }

        // ROI da IA
        BigDecimal roiIAValor = vendaRepository.calcularFaturamentoInfluenciaIA(inicioRef, fimRef);
        Long roiIAItens = vendaRepository.contarItensInfluenciaIA(inicioRef, fimRef);
        inteligenciaVendas.put("roiValor", roiIAValor != null ? roiIAValor : BigDecimal.ZERO);
        inteligenciaVendas.put("roiItens", roiIAItens != null ? roiIAItens : 0L);

        Map<String, Object> inventarioNode = new HashMap<>();
        inventarioNode.put("indiceRuptura", calcularRuptura());
        inventarioNode.put("produtosVencendo", riscoVencendo);
        inventarioNode.put("estoqueCurvaC", riscoCurvaC);
        inventarioNode.put("vendasPerdidas", vendasPerdidas);

        Map<String, Object> financeiro = new HashMap<>();
        // O NOME DAS VARIÁVEIS AQUI NÃO MUDA PARA NÃO QUEBRAR O FRONTEND, MAS OS DADOS RESPEITAM O FILTRO
        financeiro.put("faturamentoHoje", faturamentoPeriodo);
        financeiro.put("descontosHoje", descontosPeriodo);
        financeiro.put("custoTotalReposicaoHoje", custoTotalPeriodo);
        financeiro.put("lucroBrutoHoje", faturamentoPeriodo.subtract(custoTotalPeriodo));
        financeiro.put("vendasHoje", quantidadeVendas);

        financeiro.put("ticketMedio", ticketMedio);
        financeiro.put("produtosDistintosPorVenda", mixProdutos);
        financeiro.put("impostoProvisorioMes", impostoMes);
        financeiro.put("formasPagamento", formasPagamentoList);
        financeiro.put("vendasPorHora", vendasPorHora);
        financeiro.put("graficoVendas", graficoVendas);
        financeiro.put("origemVendas", origemVendas);
        financeiro.put("taxaRecorrencia", taxaRecorrencia);
        financeiro.put("fluxoCaixa7Dias", fluxo7Dias); // Fluxo real de Contas
        financeiro.put("crescimentoMoM", crescimentoMoM);
        financeiro.put("runRate", runRate);

        Map<String, Object> response = new HashMap<>();
        response.put("financeiro", financeiro);
        response.put("topCategorias", topCategorias);
        response.put("topProdutos", vendaRepository.buscarRankingProdutos(inicioRef, fimRef, PageRequest.of(0, 10)));
        response.put("performanceVendedores", performanceVendedoresPeriodo(vendasDoPeriodo));
        response.put("inventario", inventarioNode);
        response.put("metaDiaria", metaMensal.divide(new BigDecimal(diasNoMes), 2, RoundingMode.HALF_UP));
        response.put("metaMensal", metaMensal);
        response.put("inteligencia", inteligenciaVendas);

        return response;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> obterListaRisco(String tipo) {
        List<Map<String, Object>> lista = new ArrayList<>();
        if ("vencimento".equals(tipo)) {
            lista.add(Map.of("produto", "Sérum Facial Anti-Idade", "estoque", 12, "custo", "R$ 450,00"));
            lista.add(Map.of("produto", "Protetor Solar FPS 50", "estoque", 8, "custo", "R$ 160,00"));
        } else {
            lista.add(Map.of("produto", "Paleta Sombras Inverno", "estoque", 25, "custo", "R$ 1.125,00"));
            lista.add(Map.of("produto", "Perfume Floral Genérico", "estoque", 14, "custo", "R$ 840,00"));
        }
        return lista;
    }

    private List<Map<String, Object>> performanceVendedoresPeriodo(List<Venda> vendas) {
        return vendas.stream().filter(v -> v.getUsuario() != null && v.getUsuario().getNome() != null)
                .collect(Collectors.groupingBy(v -> v.getUsuario().getNome()))
                .entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("nome", e.getKey());
                    m.put("converteu", (long) e.getValue().size());
                    m.put("vendas", e.getValue().stream().map(v -> safeBigDecimal(v.getValorTotal())).reduce(BigDecimal.ZERO, BigDecimal::add));
                    return m;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("vendas")).compareTo((BigDecimal) a.get("vendas")))
                .collect(Collectors.toList());
    }

    private String calcularRuptura() {
        long total = produtoRepository.count();
        long zerados = produtoRepository.countByQuantidadeEmEstoqueLessThanEqualAndAtivoTrue(0);
        return total > 0 ? String.format(Locale.US, "%.1f", ((double) zerados / total) * 100) : "0.0";
    }

    @Transactional(readOnly = true)
    public DashboardDTO carregarDashboard() { return null; }
    @Transactional(readOnly = true)
    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) { return new FiscalDashboardDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, Collections.emptyList(), Collections.emptyList()); }
    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoGeral() { return DashboardResumoDTO.builder().build(); }
    @Transactional(readOnly = true)
    public List<AuditoriaRequestDTO> buscarAlertasRecentes() { return Collections.emptyList(); }
    private BigDecimal safeBigDecimal(BigDecimal valor) { return valor != null ? valor : BigDecimal.ZERO; }
}