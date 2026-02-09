package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
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
@RequiredArgsConstructor // Mais limpo que @Autowired em todos os campos
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

        // Definição de Períodos
        LocalDateTime inicioDia = agora.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = agora.toLocalDate().atTime(LocalTime.MAX);

        LocalDateTime inicioMes = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = agora.toLocalDate().withDayOfMonth(agora.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);

        // 1. Cards (Queries ajustadas para aceitar NULL)
        BigDecimal fatHoje = safeBigDecimal(vendaRepository.somarFaturamento(inicioDia, fimDia));
        BigDecimal fatMes = safeBigDecimal(vendaRepository.somarFaturamento(inicioMes, fimMes));
        Long vendasHoje = vendaRepository.contarVendas(inicioDia, fimDia);
        if(vendasHoje == null) vendasHoje = 0L;

        BigDecimal ticketMedio = (vendasHoje > 0)
                ? fatHoje.divide(BigDecimal.valueOf(vendasHoje), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 2. Gráfico: Vendas por Dia
        List<Venda> vendasDoMes = vendaRepository.buscarVendasPorPeriodo(inicioMes, fimMes);
        Map<String, BigDecimal> mapaDias = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        // Preenche dias com 0 para o gráfico não ficar buracado
        for (int i = 1; i <= agora.getDayOfMonth(); i++) {
            String dia = agora.toLocalDate().withDayOfMonth(i).format(fmt);
            mapaDias.put(dia, BigDecimal.ZERO);
        }

        for (Venda v : vendasDoMes) {
            String dia = v.getDataVenda().format(fmt);
            mapaDias.merge(dia, v.getValorTotal(), BigDecimal::add);
        }

        List<VendaDiariaDTO> graficoVendas = mapaDias.entrySet().stream()
                .map(e -> new VendaDiariaDTO(e.getKey(), e.getValue(), 0L))
                .collect(Collectors.toList());

        // 3. Gráfico: Pagamentos (CORRIGIDO: Usa o novo método do Repository)
        List<VendaPorPagamentoDTO> graficoPagamentos = vendaRepository.agruparPorFormaPagamento(inicioMes, fimMes);

        // 4. Ranking
        List<ProdutoRankingDTO> ranking = vendaRepository.buscarRankingProdutos(inicioMes, fimMes, PageRequest.of(0, 5));

        // 5. Últimas Vendas
        List<VendaResponseDTO> ultimas = vendaRepository.findTop5Recentes(StatusFiscal.CANCELADA)
                .stream().map(VendaResponseDTO::new).collect(Collectors.toList());

        return new DashboardDTO(fatHoje, fatMes, vendasHoje, ticketMedio, graficoVendas, graficoPagamentos, ranking, ultimas);
    }

    // =========================================================================
    // 2. DASHBOARD FISCAL
    // =========================================================================
    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) {
        List<Venda> vendas = vendaRepository.buscarVendasPorPeriodo(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX));

        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal totalIBS = BigDecimal.ZERO;
        BigDecimal totalCBS = BigDecimal.ZERO;
        BigDecimal totalSeletivo = BigDecimal.ZERO;

        Map<String, FiscalDashboardDTO.FiscalDiarioDTO> diarioMap = new TreeMap<>();

        for (Venda v : vendas) {
            BigDecimal ibs = safeBigDecimal(v.getValorIbs());
            BigDecimal cbs = safeBigDecimal(v.getValorCbs());
            BigDecimal seletivo = safeBigDecimal(v.getValorIs());
            BigDecimal totalVenda = safeBigDecimal(v.getValorTotal());

            faturamento = faturamento.add(totalVenda);
            totalIBS = totalIBS.add(ibs);
            totalCBS = totalCBS.add(cbs);
            totalSeletivo = totalSeletivo.add(seletivo);

            String dia = v.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM"));

            diarioMap.merge(dia,
                    new FiscalDashboardDTO.FiscalDiarioDTO(dia, ibs, cbs, totalVenda),
                    (a, b) -> new FiscalDashboardDTO.FiscalDiarioDTO(
                            dia,
                            a.ibs().add(b.ibs()),
                            a.cbs().add(b.cbs()),
                            a.vendas().add(b.vendas())
                    )
            );
        }

        BigDecimal totalImpostos = totalIBS.add(totalCBS).add(totalSeletivo);
        BigDecimal totalRetido = totalImpostos; // Simulação Split Payment

        double aliquotaEfetiva = 0.0;
        if (faturamento.compareTo(BigDecimal.ZERO) > 0) {
            aliquotaEfetiva = totalImpostos
                    .divide(faturamento, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100))
                    .doubleValue();
        }

        return new FiscalDashboardDTO(
                faturamento,
                totalIBS,
                totalCBS,
                totalSeletivo,
                totalRetido,
                aliquotaEfetiva,
                new ArrayList<>(diarioMap.values()),
                List.of(
                        new FiscalDashboardDTO.FiscalDistribuicaoDTO("IBS", totalIBS),
                        new FiscalDashboardDTO.FiscalDistribuicaoDTO("CBS", totalCBS),
                        new FiscalDashboardDTO.FiscalDistribuicaoDTO("Seletivo", totalSeletivo)
                )
        );
    }

    // =========================================================================
    // 3. OUTROS MÉTODOS (Operacional e Alertas)
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
                .ultimasAlteracoes(auditoriaService.listarUltimosEventos(5))
                .build();
    }

    @Transactional(readOnly = true)
    public List<AuditoriaRequestDTO> buscarAlertasRecentes() {
        return auditoriaService.listarUltimosEventos(5);
    }

    private BigDecimal safeBigDecimal(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}