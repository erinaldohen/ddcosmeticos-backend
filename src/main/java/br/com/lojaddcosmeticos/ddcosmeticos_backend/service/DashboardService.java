package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

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
                .ultimasAlteracoes(auditoriaService.listarUltimasAlteracoes(5))
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
        return auditoriaService.listarUltimasAlteracoes(5);
    }

    // =========================================================================
    // 3. AUXILIARES
    // =========================================================================

    private BigDecimal safeBigDecimal(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}