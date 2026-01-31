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
    // 1. DASHBOARD DE VENDAS
    // =========================================================================
    // Adicione os imports necessários:
    @Transactional(readOnly = true)
    public DashboardDTO carregarDashboard() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime inicioDia = agora.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = agora.toLocalDate().atTime(LocalTime.MAX);
        LocalDateTime inicioMes = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimMes = agora.toLocalDate().withDayOfMonth(agora.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);

        // 1. Cards
        BigDecimal fatHoje = vendaRepository.somarFaturamento(inicioDia, fimDia);
        BigDecimal fatMes = vendaRepository.somarFaturamento(inicioMes, fimMes);
        Long vendasHoje = vendaRepository.contarVendas(inicioDia, fimDia);
        Long vendasMes = vendaRepository.contarVendas(inicioMes, fimMes);

        BigDecimal ticketMedio = (vendasMes > 0)
                ? fatMes.divide(BigDecimal.valueOf(vendasMes), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 2. Gráfico Barras (Dias)
        List<Venda> vendasDoMes = vendaRepository.buscarVendasPorPeriodo(inicioMes, fimMes);
        Map<String, BigDecimal> mapaDias = new TreeMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        for (int i = 1; i <= agora.getDayOfMonth(); i++) {
            mapaDias.put(agora.toLocalDate().withDayOfMonth(i).format(fmt), BigDecimal.ZERO);
        }
        for (Venda v : vendasDoMes) {
            mapaDias.merge(v.getDataVenda().format(fmt), v.getValorTotal(), BigDecimal::add);
        }
        List<VendaDiariaDTO> graficoVendas = mapaDias.entrySet().stream()
                .map(e -> new VendaDiariaDTO(e.getKey(), e.getValue(), 0L))
                .collect(Collectors.toList());

        // 3. Gráfico Pizza (Pagamentos)
        List<VendaPorPagamentoDTO> graficoPagamentos = vendaRepository.agruparPorPagamento(inicioMes, fimMes);

        // 4. Ranking (Top 5 Produtos)
        List<ProdutoRankingDTO> ranking = vendaRepository.buscarRankingProdutos(inicioMes, fimMes, PageRequest.of(0, 5));

        // 5. Últimas Vendas (Top 5)
        List<VendaResponseDTO> ultimas = vendaRepository.findTop5ByStatusNfceNotOrderByDataVendaDesc(StatusFiscal.CANCELADA)
                .stream().map(VendaResponseDTO::new).collect(Collectors.toList());

        return new DashboardDTO(fatHoje, fatMes, vendasHoje, ticketMedio, graficoVendas, graficoPagamentos, ranking, ultimas);
    }

    // =========================================================================
    // 2. RESUMO OPERACIONAL
    // =========================================================================
    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoGeral() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);
        LocalDate hoje = LocalDate.now();

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

    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) {
        return null; // Implementação mantida no original
    }
}