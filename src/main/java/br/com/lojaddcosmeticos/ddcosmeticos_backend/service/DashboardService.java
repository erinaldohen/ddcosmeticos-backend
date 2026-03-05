package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEvento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
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
    // 1. DASHBOARD DE VENDAS (Tela Principal) - TEMPO REAL
    // =========================================================================

    // REMOVIDO: @Cacheable (Para garantir que a venda apareça no mesmo segundo no painel)
    @Transactional(readOnly = true)
    public Map<String, Object> obterDadosDoDashboard() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicioDia = agora.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = agora.toLocalDate().atTime(LocalTime.MAX);
        LocalDateTime inicioMes = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = agora.toLocalDate().withDayOfMonth(agora.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);

        // --- 1. FATURAMENTO E LUCRO BRUTO HOJE ---
        List<Venda> vendasHoje = vendaRepository.buscarVendasPorPeriodo(inicioDia, fimDia);

        BigDecimal faturamentoHoje = vendasHoje.stream()
                .map(v -> safeBigDecimal(v.getValorTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal custoTotalHoje = vendasHoje.stream()
                .flatMap(v -> v.getItens().stream())
                .map(i -> {
                    BigDecimal custo = safeBigDecimal(i.getCustoUnitarioHistorico());
                    BigDecimal qtd = i.getQuantidade() != null ? new BigDecimal(i.getQuantidade().toString()) : BigDecimal.ZERO;
                    return custo.multiply(qtd);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
            BigDecimal valorNaHora = vendasNaHora.stream().map(V -> safeBigDecimal(V.getValorTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> mapHora = new HashMap<>();
            mapHora.put("hora", hora);
            mapHora.put("valorTotal", valorNaHora);
            mapHora.put("quantidadeVendas", vendasNaHora.size());
            vendasPorHora.add(mapHora);
        }

        // --- 3. MIX DE PAGAMENTOS (Gráfico de Rosca) ---
        Map<String, BigDecimal> mixPagamentos = new LinkedHashMap<>();
        mixPagamentos.put("PIX", BigDecimal.ZERO);
        mixPagamentos.put("Crédito", BigDecimal.ZERO);
        mixPagamentos.put("Débito", BigDecimal.ZERO);
        mixPagamentos.put("Dinheiro", BigDecimal.ZERO);

        vendasHoje.stream()
                .filter(v -> v.getPagamentos() != null)
                .flatMap(v -> v.getPagamentos().stream())
                .forEach(p -> {
                    if (p.getFormaPagamento() == null) return;
                    BigDecimal valorPgto = safeBigDecimal(p.getValor());
                    switch (p.getFormaPagamento()) {
                        case PIX -> mixPagamentos.merge("PIX", valorPgto, BigDecimal::add);
                        case CREDITO, CARTAO_CREDITO -> mixPagamentos.merge("Crédito", valorPgto, BigDecimal::add);
                        case DEBITO, CARTAO_DEBITO -> mixPagamentos.merge("Débito", valorPgto, BigDecimal::add);
                        case DINHEIRO -> mixPagamentos.merge("Dinheiro", valorPgto, BigDecimal::add);
                        default -> mixPagamentos.merge("Outros", valorPgto, BigDecimal::add);
                    }
                });

        List<Map<String, Object>> pagamentosDadosReais = new ArrayList<>();
        mixPagamentos.forEach((nome, valor) -> {
            if (valor.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> fatia = new HashMap<>();
                fatia.put("name", nome);
                fatia.put("value", valor);
                // Injeta a cor exata que o React espera
                if (nome.equals("PIX")) fatia.put("color", "#10b981");
                else if (nome.equals("Crédito")) fatia.put("color", "#8b5cf6");
                else if (nome.equals("Débito")) fatia.put("color", "#3b82f6");
                else if (nome.equals("Dinheiro")) fatia.put("color", "#f59e0b");
                else fatia.put("color", "#94a3b8");

                pagamentosDadosReais.add(fatia);
            }
        });

        // --- 4. GRÁFICO: VENDAS DO MÊS ---
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

        // --- 5. TOP PRODUTOS E AUDITORIA ---
        List<ProdutoRankingDTO> ranking = vendaRepository.buscarRankingProdutos(inicioMes, fimMes, PageRequest.of(0, 5));
        List<Map<String, Object>> topProdutos = ranking.stream().map(r -> {
            Map<String, Object> p = new HashMap<>();
            p.put("produto", r.produto());
            p.put("quantidade", r.quantidade());
            p.put("valorTotal", r.valorTotal());
            return p;
        }).collect(Collectors.toList());

        List<AuditoriaRequestDTO> auditoriaRecente = auditoriaService.listarUltimosEventos(10).stream()
                .map(a -> new AuditoriaRequestDTO(
                        a.tipoEvento() != null ? a.tipoEvento() : TipoEvento.INFO,
                        a.mensagem(),
                        a.usuarioResponsavel() != null ? a.usuarioResponsavel() : "Sistema",
                        a.dataHora()
                )).collect(Collectors.toList());

        // --- 6. MONTAGEM DA RESPOSTA FINAL ---
        Map<String, Object> response = new HashMap<>();

        Map<String, Object> financeiro = new HashMap<>();
        financeiro.put("faturamentoHoje", faturamentoHoje);
        financeiro.put("lucroBrutoHoje", lucroBrutoHoje);
        financeiro.put("vendasHoje", quantidadeVendas);
        financeiro.put("ticketMedio", ticketMedio);
        financeiro.put("graficoVendas", graficoVendas);
        financeiro.put("vendasPorHora", vendasPorHora);
        financeiro.put("pagamentos", pagamentosDadosReais); // Gráfico de Rosca atualizado com dados reais!

        response.put("financeiro", financeiro);
        response.put("inventario", Map.of("baixoEstoque", produtoRepository.countBaixoEstoque(), "produtosVencidos", produtoRepository.countVencidos()));
        response.put("topProdutos", topProdutos);
        response.put("auditoria", auditoriaRecente);

        return response;
    }

    // =========================================================================
    // 2. MÉTODOS ORIGINAIS (MANTIDOS INTACTOS)
    // =========================================================================

    @Transactional(readOnly = true)
    public DashboardDTO carregarDashboard() {
        return null;
    }

    @Transactional(readOnly = true)
    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) {
        return new FiscalDashboardDTO(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, Collections.emptyList(), Collections.emptyList()
        );
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
                .map(a -> new AuditoriaRequestDTO(
                        a.tipoEvento() != null ? a.tipoEvento() : TipoEvento.INFO,
                        a.mensagem(),
                        a.usuarioResponsavel() != null ? a.usuarioResponsavel() : "Sistema",
                        a.dataHora()
                ))
                .collect(Collectors.toList());
    }

    private BigDecimal safeBigDecimal(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}