package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class DashboardService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;

    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private AuditoriaService auditoriaService;

    // =========================================================================
    // 1. MÉTODO PRINCIPAL (Resumo Geral para o Painel)
    // =========================================================================
    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoGeral() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);
        LocalDate hoje = LocalDate.now();

        // 1. Coleta de Vendas (Null Safety)
        Long qtdVendas = vendaRepository.countByDataVendaBetween(inicioDia, fimDia);
        if (qtdVendas == null) qtdVendas = 0L;

        BigDecimal totalVendido = vendaRepository.sumTotalVendaByDataVendaBetween(inicioDia, fimDia);
        if (totalVendido == null) totalVendido = BigDecimal.ZERO;

        // 2. Coleta Financeira (Saldo do Dia)
        // Somar contas PENDENTES que vencem hoje (ou PAGAS, dependendo da regra de fluxo de caixa)
        BigDecimal pagarHoje = contaPagarRepository.sumValorByDataVencimentoAndStatus(hoje, StatusConta.PENDENTE);
        if (pagarHoje == null) pagarHoje = BigDecimal.ZERO;

        BigDecimal receberHoje = contaReceberRepository.sumValorByDataVencimento(hoje);
        if (receberHoje == null) receberHoje = BigDecimal.ZERO;

        // Cálculo do Saldo Líquido do Dia: (Receber + Vendido no PDV) - Contas a Pagar
        BigDecimal saldoDia = receberHoje.add(totalVendido).subtract(pagarHoje);

        // 3. Coleta de Atrasados
        BigDecimal vencidoPagar = contaPagarRepository.sumValorByDataVencimentoBeforeAndStatus(hoje, StatusConta.PENDENTE);
        if (vencidoPagar == null) vencidoPagar = BigDecimal.ZERO;

        // 4. Construção do DTO
        return DashboardResumoDTO.builder()
                // --- Estoque ---
                .produtosAbaixoMinimo(produtoRepository.contarProdutosAbaixoDoMinimo())
                .produtosEsgotados(produtoRepository.countByQuantidadeEmEstoqueLessThanEqualAndAtivoTrue(0))
                .valorTotalEstoqueCusto(safeBigDecimal(produtoRepository.calcularValorTotalEstoque()))

                // --- Financeiro & Vendas ---
                .quantidadeVendasHoje(qtdVendas)
                .totalVendidoHoje(totalVendido)
                .saldoDoDia(saldoDia)
                .totalVencidoPagar(vencidoPagar)

                // --- Fiscal & Auditoria ---
                .produtosMargemCritica((long) precificacaoService.buscarProdutosComMargemCritica().size())
                .produtosSemNcmOuCest(produtoRepository.contarProdutosSemFiscal())
                // Aqui chamamos o método que corrigimos no AuditoriaService para retornar AuditoriaRequestDTO
                .ultimasAlteracoes(auditoriaService.listarUltimosEventos(5))
                .build();
    }

    // =========================================================================
    // 2. MÉTODOS ESPECÍFICOS (Para chamadas AJAX individuais)
    // =========================================================================

    /**
     * Este método estava faltando e gerava erro no Controller.
     * Ele retorna apenas os alertas, útil para polling (atualização automática) no Frontend.
     */
    @Transactional(readOnly = true)
    public List<AuditoriaRequestDTO> buscarAlertasRecentes() {
        // Busca as 5 últimas atividades do sistema
        return auditoriaService.listarUltimosEventos(5);
    }

    // =========================================================================
    // 3. AUXILIARES
    // =========================================================================

    private BigDecimal safeBigDecimal(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) {
        // 1. Busca vendas no período
        List<Venda> vendas = vendaRepository.findByDataVendaBetween(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX));

        // 2. Variáveis acumuladoras
        BigDecimal faturamento = BigDecimal.ZERO;
        BigDecimal totalIBS = BigDecimal.ZERO;
        BigDecimal totalCBS = BigDecimal.ZERO;
        BigDecimal totalSeletivo = BigDecimal.ZERO;

        // Mapa para agrupar por dia (Ordenado)
        Map<String, FiscalDashboardDTO.FiscalDiarioDTO> diarioMap = new TreeMap<>();

        for (Venda v : vendas) {
            // Tratamento de nulos para evitar crash
            BigDecimal ibs = v.getValorIbs() != null ? v.getValorIbs() : BigDecimal.ZERO;
            BigDecimal cbs = v.getValorCbs() != null ? v.getValorCbs() : BigDecimal.ZERO;
            BigDecimal seletivo = v.getValorIs() != null ? v.getValorIs() : BigDecimal.ZERO;
            BigDecimal totalVenda = v.getValorTotal() != null ? v.getValorTotal() : BigDecimal.ZERO;

            faturamento = faturamento.add(totalVenda);
            totalIBS = totalIBS.add(ibs);
            totalCBS = totalCBS.add(cbs);
            totalSeletivo = totalSeletivo.add(seletivo);

            // Agrupa histórico por dia (ex: "01/10")
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

        // Simulação do Split Payment (Total Retido = Impostos)
        BigDecimal totalRetido = totalImpostos;

        // Cálculo Alíquota Efetiva Segura
        double aliquotaEfetiva = 0.0;
        if (faturamento.compareTo(BigDecimal.ZERO) > 0) {
            aliquotaEfetiva = totalImpostos
                    .divide(faturamento, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100))
                    .doubleValue();
        }

        // Montagem do Retorno
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
}