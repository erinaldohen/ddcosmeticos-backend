package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO; // Importante
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
import org.springframework.beans.factory.annotation.Autowired;
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
public class DashboardService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private AuditoriaService auditoriaService;

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
        BigDecimal fatHoje = vendaRepository.somarFaturamento(inicioDia, fimDia);
        BigDecimal fatMes = vendaRepository.somarFaturamento(inicioMes, fimMes);
        Long vendasHoje = vendaRepository.contarVendas(inicioDia, fimDia);
        // Long vendasMes = vendaRepository.contarVendas(inicioMes, fimMes); // Não usado no DTO atual, mas útil saber

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

        for (Venda v : vendasDoMes) {
            String dia = v.getDataVenda().format(fmt);
            mapaDias.merge(dia, v.getValorTotal(), BigDecimal::add);
        }

        List<VendaDiariaDTO> graficoVendas = mapaDias.entrySet().stream()
                .map(e -> new VendaDiariaDTO(e.getKey(), e.getValue(), 0L))
                .collect(Collectors.toList());

        // 3. Gráfico: Pagamentos
        List<VendaPorPagamentoDTO> graficoPagamentos = vendaRepository.agruparPorPagamento(inicioMes, fimMes);

        // 4. Ranking
        List<ProdutoRankingDTO> ranking = vendaRepository.buscarRankingProdutos(inicioMes, fimMes, PageRequest.of(0, 5));

        // 5. Últimas Vendas
        List<VendaResponseDTO> ultimas = vendaRepository.findTop5Recentes(StatusFiscal.CANCELADA)
                .stream().map(VendaResponseDTO::new).collect(Collectors.toList());

        return new DashboardDTO(fatHoje, fatMes, vendasHoje, ticketMedio, graficoVendas, graficoPagamentos, ranking, ultimas);
    }

    // =========================================================================
    // 2. DASHBOARD FISCAL (O Método que faltava!)
    // =========================================================================
    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) {
        // Usamos buscarVendasPorPeriodo para garantir que só pegamos vendas VÁLIDAS (Não canceladas)
        List<Venda> vendas = vendaRepository.buscarVendasPorPeriodo(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX));

        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal totalIBS = BigDecimal.ZERO;
        BigDecimal totalCBS = BigDecimal.ZERO;
        BigDecimal totalSeletivo = BigDecimal.ZERO;

        Map<String, FiscalDashboardDTO.FiscalDiarioDTO> diarioMap = new TreeMap<>();

        for (Venda v : vendas) {
            BigDecimal ibs = v.getValorIbs() != null ? v.getValorIbs() : BigDecimal.ZERO;
            BigDecimal cbs = v.getValorCbs() != null ? v.getValorCbs() : BigDecimal.ZERO;
            BigDecimal seletivo = v.getValorIs() != null ? v.getValorIs() : BigDecimal.ZERO;
            BigDecimal totalVenda = v.getValorTotal() != null ? v.getValorTotal() : BigDecimal.ZERO;

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

        // Null Safety na query antiga
        Long qtdVendas = vendaRepository.countByDataVendaBetween(inicioDia, fimDia);
        BigDecimal totalVendido = vendaRepository.sumTotalVendaByDataVendaBetween(inicioDia, fimDia);
        if (totalVendido == null) totalVendido = BigDecimal.ZERO;

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