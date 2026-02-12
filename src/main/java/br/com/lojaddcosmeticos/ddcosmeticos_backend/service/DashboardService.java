package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
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
    // 1. DASHBOARD DE VENDAS (Tela Principal)
    // =========================================================================
    @Transactional(readOnly = true)
    public DashboardDTO carregarDashboard() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicioDia = agora.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = agora.toLocalDate().atTime(LocalTime.MAX);
        LocalDateTime inicioMes = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = agora.toLocalDate().withDayOfMonth(agora.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);

        // 1. Cards
        BigDecimal fatHoje = safeBigDecimal(vendaRepository.somarFaturamento(inicioDia, fimDia));
        Long vendasHoje = vendaRepository.contarVendas(inicioDia, fimDia);
        if(vendasHoje == null) vendasHoje = 0L;

        BigDecimal ticketMedio = (vendasHoje > 0)
                ? fatHoje.divide(BigDecimal.valueOf(vendasHoje), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 2. Gráfico: Vendas por Dia
        List<Venda> vendasDoMes = vendaRepository.buscarVendasPorPeriodo(inicioMes, fimMes);
        Map<String, BigDecimal> mapaDias = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        // Preenche dias com 0
        for (int i = 1; i <= agora.getDayOfMonth(); i++) {
            String dia = agora.toLocalDate().withDayOfMonth(i).format(fmt);
            mapaDias.put(dia, BigDecimal.ZERO);
        }

        // Popula com dados reais
        for (Venda v : vendasDoMes) {
            if (v.getDataVenda() != null) {
                String dia = v.getDataVenda().format(fmt);
                mapaDias.merge(dia, v.getValorTotal(), BigDecimal::add);
            }
        }

        // Converte Map para DTO
        List<VendaDiariaDTO> graficoVendas = mapaDias.entrySet().stream()
                .map(e -> {
                    LocalDate dataAprox = LocalDate.parse(e.getKey() + "/" + agora.getYear(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    return new VendaDiariaDTO(dataAprox, e.getValue(), 0L);
                })
                .collect(Collectors.toList());

        // 3. Gráficos de Pagamento e Ranking
        List<VendaPorPagamentoDTO> graficoPagamentos = vendaRepository.agruparPorFormaPagamento(inicioMes, fimMes);
        List<ProdutoRankingDTO> ranking = vendaRepository.buscarRankingProdutos(inicioMes, fimMes, PageRequest.of(0, 5));

        // 4. Últimas Vendas
        List<VendaResponseDTO> ultimas = vendaRepository.findTop5ByOrderByDataVendaDesc()
                .stream().map(VendaResponseDTO::new).collect(Collectors.toList());

        // 5. Auditoria
        // NOTA: Se 'AuditoriaRequestDTO' for a classe que você quer retornar no DashboardDTO,
        // o DashboardDTO deve declarar `List<AuditoriaRequestDTO> auditoria` no seu Record.
        List<AuditoriaRequestDTO> auditoriaRecente = auditoriaService.listarUltimosEventos(5).stream()
                .map(a -> new AuditoriaRequestDTO(
                        a.tipoEvento() != null ? a.tipoEvento() : TipoEvento.INFO,
                        a.mensagem(),
                        a.usuarioResponsavel() != null ? a.usuarioResponsavel() : "Sistema",
                        a.dataHora()
                ))
                .collect(Collectors.toList());

        // --- CONSTRUÇÃO DOS DTOS DO DASHBOARD ---

        DashboardDTO.FinanceiroDTO financeiro = new DashboardDTO.FinanceiroDTO(
                fatHoje,
                vendasHoje.intValue(),
                ticketMedio,
                // Gráfico Vendas: Ajuste para acessar campos do Record VendaDiariaDTO
                graficoVendas.stream()
                        .map(v -> new DashboardDTO.GraficoVendaDTO(
                                v.data().format(fmt), // Record: v.data()
                                v.total()             // Record: v.total()
                        )).toList(),
                // Gráfico Pagamentos: Ajuste para acessar campos do DTO (Se for Record ou Class com @Data)
                graficoPagamentos.stream()
                        .map(p -> new DashboardDTO.GraficoPagamentoDTO(
                                // Se for Record: p.formaPagamento(), Se for Classe: p.getFormaPagamento()
                                // Vou assumir Record para manter consistência com o resto
                                p.formaPagamento(),
                                p.valorTotal()
                        )).toList()
        );

        long countVencidos = produtoRepository.countVencidos();
        long countBaixoEstoque = produtoRepository.countBaixoEstoque();
        DashboardDTO.InventarioDTO inventario = new DashboardDTO.InventarioDTO(countVencidos, countBaixoEstoque);

        List<DashboardDTO.TopProdutoDTO> rankingDto = ranking.stream()
                .map(r -> new DashboardDTO.TopProdutoDTO(r.produto(), r.quantidade(), r.valorTotal()))
                .collect(Collectors.toList());

        List<DashboardDTO.UltimaVendaDTO> ultimasDto = ultimas.stream()
                .map(u -> new DashboardDTO.UltimaVendaDTO(
                        u.id(),
                        u.clienteNome(),
                        u.valorTotal(),
                        u.pagamentos().stream().map(p -> new DashboardDTO.PagamentoResumoDTO(p.formaPagamento(), p.valor())).toList()
                ))
                .collect(Collectors.toList());

        // AQUI: Passando auditoriaRecente (que é List<AuditoriaRequestDTO>)
        // O DashboardDTO deve estar definido para aceitar List<AuditoriaRequestDTO> no 3º parâmetro
        return new DashboardDTO(financeiro, inventario, auditoriaRecente , rankingDto, ultimasDto);
    }

    // =========================================================================
    // 2. DASHBOARD FISCAL
    // =========================================================================
    @Transactional(readOnly = true)
    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) {
        // CORREÇÃO: O DTO agora exige 8 parâmetros.
        // Passando valores zerados e listas vazias para compilar.
        return new FiscalDashboardDTO(
                BigDecimal.ZERO, // 1. Total Faturamento
                BigDecimal.ZERO, // 2. Total Impostos
                BigDecimal.ZERO, // 3. Total ICMS
                BigDecimal.ZERO, // 4. Total PIS
                BigDecimal.ZERO, // 5. Total COFINS
                0.0,             // 6. Carga Tributária Média (Double)
                Collections.emptyList(), // 7. Lista de Gráfico Diário
                Collections.emptyList()  // 8. Lista de Distribuição
        );
    }

    // =========================================================================
    // 3. OUTROS MÉTODOS
    // =========================================================================
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