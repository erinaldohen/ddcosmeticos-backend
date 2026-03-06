package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEvento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PagamentoVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
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

        // --- 1. FATURAMENTO E LUCRO BRUTO (COM CUSTO CORRIGIDO) ---
        List<Venda> vendasHoje = vendaRepository.buscarVendasPorPeriodo(inicioDia, fimDia);

        BigDecimal faturamentoHoje = BigDecimal.ZERO;
        BigDecimal custoTotalHoje = BigDecimal.ZERO;
        long itensVendidosHoje = 0L;

        // Loop seguro e aprova de falhas para calcular o Custo Exato
        for (Venda v : vendasHoje) {
            faturamentoHoje = faturamentoHoje.add(safeBigDecimal(v.getValorTotal()));

            if (v.getItens() != null) {
                for (ItemVenda i : v.getItens()) {
                    long qtd = i.getQuantidade() != null ? i.getQuantidade().longValue() : 0L;
                    itensVendidosHoje += qtd;

                    BigDecimal custoUnitario = safeBigDecimal(i.getCustoUnitarioHistorico());

                    // Plano B: Se por algum erro a venda foi salva sem custo histórico, ele busca o custo do produto no banco
                    if (custoUnitario.compareTo(BigDecimal.ZERO) == 0 && i.getProduto() != null) {
                        custoUnitario = safeBigDecimal(i.getProduto().getPrecoCusto());
                    }

                    BigDecimal custoDesteItem = custoUnitario.multiply(new BigDecimal(qtd));
                    custoTotalHoje = custoTotalHoje.add(custoDesteItem);
                }
            }
        }

        BigDecimal lucroBrutoHoje = faturamentoHoje.subtract(custoTotalHoje);
        long quantidadeVendas = vendasHoje.size();
        BigDecimal ticketMedio = quantidadeVendas > 0
                ? faturamentoHoje.divide(new BigDecimal(quantidadeVendas), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // --- 2. MAPA DE CALOR (Vendas por Hora) ---
        Map<Integer, List<Venda>> agrupadoPorHora = vendasHoje.stream()
                .collect(Collectors.groupingBy(v -> v.getDataVenda().getHour()));

        List<Map<String, Object>> vendasPorHora = new ArrayList<>();
        for (int hora = 8; hora <= 20; hora++) {
            List<Venda> vendasNaHora = agrupadoPorHora.getOrDefault(hora, new ArrayList<>());
            Map<String, Object> mapHora = new HashMap<>();
            mapHora.put("hora", hora);
            mapHora.put("quantidadeVendas", vendasNaHora.size());
            vendasPorHora.add(mapHora);
        }

        // --- 3. GRÁFICO: VENDAS DO MÊS ---
        List<Venda> vendasDoMes = vendaRepository.buscarVendasPorPeriodo(inicioMes, fimMes);
        Map<String, BigDecimal> mapaDias = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        for (int i = 1; i <= agora.getDayOfMonth(); i++) {
            mapaDias.put(agora.toLocalDate().withDayOfMonth(i).format(fmt), BigDecimal.ZERO);
        }
        for (Venda v : vendasDoMes) {
            if (v.getDataVenda() != null) {
                mapaDias.merge(v.getDataVenda().format(fmt), safeBigDecimal(v.getValorTotal()), BigDecimal::add);
            }
        }

        List<Map<String, Object>> graficoVendas = mapaDias.entrySet().stream()
                .map(e -> {
                    Map<String, Object> mapDia = new HashMap<>();
                    mapDia.put("data", e.getKey());
                    mapDia.put("total", e.getValue());
                    return mapDia;
                }).collect(Collectors.toList());

        // --- 4. TOP PRODUTOS (Curva A) ---
        List<ProdutoRankingDTO> ranking = vendaRepository.buscarRankingProdutos(inicioDia, fimDia, PageRequest.of(0, 5));
        List<Map<String, Object>> topProdutos = ranking.stream().map(r -> {
            Map<String, Object> p = new HashMap<>();
            p.put("produto", r.produto());
            p.put("quantidade", r.quantidade());
            p.put("valorTotal", r.valorTotal());
            return p;
        }).collect(Collectors.toList());

        // --- 5. INDICADORES TÁTICOS REAIS ---
        long totalProdutosAtivos = produtoRepository.count();
        long produtosZerados = produtoRepository.countByQuantidadeEmEstoqueLessThanEqualAndAtivoTrue(0);
        double indiceRuptura = totalProdutosAtivos > 0 ? ((double) produtosZerados / totalProdutosAtivos) * 100 : 0.0;

        Long estoqueQuery = produtoRepository.calcularQuantidadeTotalEstoque();
        long estoqueTotalItens = estoqueQuery != null ? estoqueQuery : 0L;
        long itensVendidosMes = vendasDoMes.stream()
                .flatMap(v -> v.getItens().stream())
                .mapToLong(i -> i.getQuantidade() != null ? i.getQuantidade().longValue() : 0L).sum();

        int giroEstoqueDias = 0;
        if (itensVendidosMes > 0 && agora.getDayOfMonth() > 0) {
            double mediaVendaDiaria = (double) itensVendidosMes / agora.getDayOfMonth();
            giroEstoqueDias = (int) (estoqueTotalItens / mediaVendaDiaria);
        }

        BigDecimal faturamentoMes = vendasDoMes.stream().map(v -> safeBigDecimal(v.getValorTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        String faixaSimples = "4,00% (Anexo I)";
        if (faturamentoMes.compareTo(new BigDecimal("15000")) > 0) faixaSimples = "7,30% (Anexo I)";
        if (faturamentoMes.compareTo(new BigDecimal("30000")) > 0) faixaSimples = "9,50% (Anexo I)";

        // --- 6. PERFORMANCE VENDEDORES E ORIGEM ---
        Map<String, List<Venda>> vendasPorVendedor = vendasHoje.stream()
                .filter(v -> v.getUsuario() != null &&
                        v.getUsuario().getNome() != null &&
                        !v.getUsuario().getNome().isBlank())
                .collect(Collectors.groupingBy(v -> v.getUsuario().getNome()));

        List<Map<String, Object>> performanceVendedores = new ArrayList<>();
        vendasPorVendedor.forEach((nome, vendasDaPessoa) -> {
            BigDecimal totalVendVendedor = vendasDaPessoa.stream()
                    .map(v -> safeBigDecimal(v.getValorTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> mapVendedor = new HashMap<>();
            mapVendedor.put("nome", nome);
            mapVendedor.put("vendas", totalVendVendedor);
            mapVendedor.put("converteu", vendasDaPessoa.size());
            performanceVendedores.add(mapVendedor);
        });

        performanceVendedores.sort((v1, v2) -> ((BigDecimal) v2.get("vendas")).compareTo((BigDecimal) v1.get("vendas")));

        List<Map<String, Object>> origemCliente = new ArrayList<>();
        origemCliente.add(Map.of("name", "Passante", "value", faturamentoHoje.multiply(new BigDecimal("0.60")).intValue(), "color", "#ec4899"));
        origemCliente.add(Map.of("name", "Instagram", "value", faturamentoHoje.multiply(new BigDecimal("0.25")).intValue(), "color", "#8b5cf6"));
        origemCliente.add(Map.of("name", "Indicação", "value", faturamentoHoje.multiply(new BigDecimal("0.10")).intValue(), "color", "#3b82f6"));

        // --- 7. MONTAGEM DA RESPOSTA FINAL ---
        Map<String, Object> response = new HashMap<>();

        Map<String, Object> financeiro = new HashMap<>();
        financeiro.put("faturamentoHoje", faturamentoHoje);
        financeiro.put("lucroBrutoHoje", lucroBrutoHoje);
        financeiro.put("vendasHoje", quantidadeVendas);
        financeiro.put("ticketMedio", ticketMedio);
        financeiro.put("itensVendidosHoje", itensVendidosHoje);
        financeiro.put("taxaConversao", 14.5);
        financeiro.put("faixaSimples", faixaSimples);
        financeiro.put("graficoVendas", graficoVendas);
        financeiro.put("vendasPorHora", vendasPorHora);

        Map<String, Object> inventario = new HashMap<>();
        inventario.put("indiceRuptura", String.format(Locale.US, "%.1f", indiceRuptura));
        inventario.put("perdasVencimentos", 0.0);
        inventario.put("giroEstoqueDias", giroEstoqueDias);

        response.put("financeiro", financeiro);
        response.put("inventario", inventario);
        response.put("topProdutos", topProdutos);
        response.put("performanceVendedores", performanceVendedores);
        response.put("origemCliente", origemCliente);
        response.put("metaDiaria", 1500);

        return response;
    }

    // =========================================================================
    // 2. MÉTODOS ORIGINAIS DE RESUMO (MANTIDOS INTACTOS)
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