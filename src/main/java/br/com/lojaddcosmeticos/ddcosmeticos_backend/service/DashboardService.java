package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEvento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
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
    private final PrecificacaoService precificacaoService;
    private final AuditoriaService auditoriaService;

    // =========================================================================
    // 1. DASHBOARD DE VENDAS E INTELIGÊNCIA - TEMPO REAL
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> obterDadosDoDashboard() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicioDia = agora.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = agora.toLocalDate().atTime(LocalTime.MAX);
        LocalDateTime inicioMes = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = agora.toLocalDate().withDayOfMonth(agora.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);

        // --- 1. FATURAMENTO, CUSTO E REPOSIÇÃO ---
        List<Venda> vendasHoje = vendaRepository.buscarVendasPorPeriodo(inicioDia, fimDia);

        BigDecimal faturamentoHoje = BigDecimal.ZERO;
        BigDecimal custoTotalHoje = BigDecimal.ZERO;
        BigDecimal descontosHoje = BigDecimal.ZERO;
        long totalLinhasItensHoje = 0L;

        // Mapas para agrupamento dinâmico
        Map<String, BigDecimal> faturamentoPorForma = new HashMap<>();
        Map<String, BigDecimal> faturamentoPorCategoria = new HashMap<>();

        for (Venda v : vendasHoje) {
            faturamentoHoje = faturamentoHoje.add(safeBigDecimal(v.getValorTotal()));
            descontosHoje = descontosHoje.add(safeBigDecimal(v.getDescontoTotal()));

            // Agrupamento por Meio de Pagamento Real
            if (v.getPagamentos() != null) {
                v.getPagamentos().forEach(p -> {
                    String forma = p.getFormaPagamento().name().replace("_", " ");
                    faturamentoPorForma.merge(forma, safeBigDecimal(p.getValor()), BigDecimal::add);
                });
            }

            if (v.getItens() != null) {
                totalLinhasItensHoje += v.getItens().size();
                for (ItemVenda i : v.getItens()) {
                    BigDecimal qtd = i.getQuantidade() != null ? i.getQuantidade() : BigDecimal.ZERO;

                    // Cálculo do Custo de Reposição (CMV)
                    BigDecimal custoUnit = safeBigDecimal(i.getCustoUnitarioHistorico());
                    if (custoUnit.compareTo(BigDecimal.ZERO) == 0 && i.getProduto() != null) {
                        custoUnit = safeBigDecimal(i.getProduto().getPrecoCusto());
                    }
                    custoTotalHoje = custoTotalHoje.add(custoUnit.multiply(qtd));

                    // Agrupamento por Categoria
                    String cat = (i.getProduto() != null && i.getProduto().getCategoria() != null)
                            ? i.getProduto().getCategoria() : "Diversos";
                    faturamentoPorCategoria.merge(cat, safeBigDecimal(i.getPrecoUnitario()).multiply(qtd), BigDecimal::add);
                }
            }
        }

        // Formatação Meios de Pagamento para o Gráfico de Rosca
        List<Map<String, Object>> formasPagamentoList = faturamentoPorForma.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    m.put("fill", e.getKey().contains("PIX") ? "#00bdae" :
                            e.getKey().contains("CARTAO") ? "#3b82f6" : "#8b5cf6");
                    return m;
                }).collect(Collectors.toList());

        // Formatação Top 3 Categorias
        List<Map<String, Object>> topCategorias = faturamentoPorCategoria.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("nome", e.getKey());
                    m.put("valor", e.getValue());
                    return m;
                }).collect(Collectors.toList());

        long quantidadeVendas = vendasHoje.size();
        BigDecimal ticketMedio = quantidadeVendas > 0 ? faturamentoHoje.divide(new BigDecimal(quantidadeVendas), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        double mixProdutos = quantidadeVendas > 0 ? (double) totalLinhasItensHoje / quantidadeVendas : 0.0;

        // --- 2. MAPA DE CALOR E EVOLUÇÃO MENSAL ---
        Map<Integer, List<Venda>> agrupadoPorHora = vendasHoje.stream().collect(Collectors.groupingBy(v -> v.getDataVenda().getHour()));
        List<Map<String, Object>> vendasPorHora = new ArrayList<>();
        for (int h = 8; h <= 20; h++) {
            Map<String, Object> m = new HashMap<>();
            m.put("horaStr", String.format("%02dh", h));
            m.put("qtd", agrupadoPorHora.getOrDefault(h, new ArrayList<>()).size());
            vendasPorHora.add(m);
        }

        List<Venda> vendasDoMes = vendaRepository.buscarVendasPorPeriodo(inicioMes, fimMes);
        Map<String, BigDecimal> mapaDias = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        vendasDoMes.forEach(v -> mapaDias.merge(v.getDataVenda().format(fmt), safeBigDecimal(v.getValorTotal()), BigDecimal::add));

        List<Map<String, Object>> graficoVendas = mapaDias.entrySet().stream().map(e -> {
            Map<String, Object> m = new HashMap<>(); m.put("data", e.getKey()); m.put("total", e.getValue()); return m;
        }).collect(Collectors.toList());

        // --- 3. INDICADORES TÁTICOS E IMPOSTOS ---
        BigDecimal faturamentoMes = vendasDoMes.stream().map(v -> safeBigDecimal(v.getValorTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal impostoMes = faturamentoMes.multiply(new BigDecimal("0.04")); // Simples Nacional Estimado

        Map<String, Object> financeiro = new HashMap<>();
        financeiro.put("faturamentoHoje", faturamentoHoje);
        financeiro.put("descontosHoje", descontosHoje);
        financeiro.put("custoTotalReposicaoHoje", custoTotalHoje);
        financeiro.put("lucroBrutoHoje", faturamentoHoje.subtract(custoTotalHoje));
        financeiro.put("vendasHoje", quantidadeVendas);
        financeiro.put("ticketMedio", ticketMedio);
        financeiro.put("produtosDistintosPorVenda", mixProdutos);
        financeiro.put("impostoProvisorioMes", impostoMes);
        financeiro.put("impostoFederal", impostoMes.multiply(new BigDecimal("0.74")));
        financeiro.put("impostoEstadual", impostoMes.multiply(new BigDecimal("0.26")));
        financeiro.put("formasPagamento", formasPagamentoList);
        financeiro.put("vendasPorHora", vendasPorHora);
        financeiro.put("graficoVendas", graficoVendas);
        financeiro.put("faixaSimples", "4,00% (Anexo I)");

        Map<String, Object> response = new HashMap<>();
        response.put("financeiro", financeiro);
        response.put("topCategorias", topCategorias);
        response.put("topProdutos", vendaRepository.buscarRankingProdutos(inicioDia, fimDia, PageRequest.of(0, 5)));
        response.put("performanceVendedores", performanceVendedoresHoje(vendasHoje));
        response.put("inventario", Map.of("indiceRuptura", calcularRuptura()));
        response.put("metaDiaria", 1500);

        return response;
    }

    private List<Map<String, Object>> performanceVendedoresHoje(List<Venda> vendas) {
        return vendas.stream()
                .filter(v -> v.getUsuario() != null && v.getUsuario().getNome() != null)
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

    // =========================================================================
    // 2. MÉTODOS DE COMPATIBILIDADE (MANTIDOS PARA OUTRAS CONTROLLERS)
    // =========================================================================

    @Transactional(readOnly = true)
    public DashboardDTO carregarDashboard() { return null; }

    @Transactional(readOnly = true)
    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) {
        return new FiscalDashboardDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, Collections.emptyList(), Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoGeral() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);
        LocalDate hoje = LocalDate.now();

        Long qtdVendas = vendaRepository.countByDataVendaBetween(inicioDia, fimDia);
        BigDecimal totalVendido = safeBigDecimal(vendaRepository.sumTotalVendaByDataVendaBetween(inicioDia, fimDia));
        BigDecimal pagarHoje = safeBigDecimal(contaPagarRepository.sumValorByDataVencimentoAndStatus(hoje, StatusConta.PENDENTE));
        BigDecimal receberHoje = safeBigDecimal(contaReceberRepository.sumValorByDataVencimento(hoje));
        BigDecimal saldoDia = receberHoje.add(totalVendido).subtract(pagarHoje);
        BigDecimal vencidoPagar = safeBigDecimal(contaPagarRepository.sumValorByDataVencimentoBeforeAndStatus(hoje, StatusConta.PENDENTE));

        return DashboardResumoDTO.builder()
                .produtosAbaixoMinimo(produtoRepository.contarProdutosAbaixoDoMinimo())
                .produtosEsgotados(produtoRepository.countByQuantidadeEmEstoqueLessThanEqualAndAtivoTrue(0))
                .valorTotalEstoqueCusto(safeBigDecimal(produtoRepository.calcularValorTotalEstoque()))
                .quantidadeVendasHoje(qtdVendas != null ? qtdVendas : 0L)
                .totalVendidoHoje(totalVendido)
                .saldoDoDia(saldoDia)
                .totalVencidoPagar(vencidoPagar)
                .produtosMargemCritica((long) precificacaoService.buscarProdutosComMargemCritica().size())
                .produtosSemNcmOuCest(produtoRepository.contarProdutosSemFiscal())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AuditoriaRequestDTO> buscarAlertasRecentes() {
        return auditoriaService.listarUltimosEventos(5).stream()
                .map(a -> new AuditoriaRequestDTO(a.tipoEvento() != null ? a.tipoEvento() : TipoEvento.INFO, a.mensagem(), a.usuarioResponsavel() != null ? a.usuarioResponsavel() : "Sistema", a.dataHora()))
                .collect(Collectors.toList());
    }

    private BigDecimal safeBigDecimal(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}