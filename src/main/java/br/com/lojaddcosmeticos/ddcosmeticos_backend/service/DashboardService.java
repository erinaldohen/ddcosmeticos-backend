package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

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

@Service
public class DashboardService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private VendaRepository vendaRepository;        // Adicionado
    @Autowired private ContaPagarRepository contaPagarRepository;     // Adicionado
    @Autowired private ContaReceberRepository contaReceberRepository; // Adicionado

    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoGeral() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);
        LocalDate hoje = LocalDate.now();

        // 1. Coleta de Vendas (Null Safety para evitar o erro do teste)
        Long qtdVendas = vendaRepository.countByDataVendaBetween(inicioDia, fimDia);
        if (qtdVendas == null) qtdVendas = 0L;

        BigDecimal totalVendido = vendaRepository.sumTotalVendaByDataVendaBetween(inicioDia, fimDia);
        if (totalVendido == null) totalVendido = BigDecimal.ZERO;

        // 2. Coleta Financeira (Saldo do Dia)
        BigDecimal pagarHoje = contaPagarRepository.sumValorByDataVencimentoAndStatus(hoje, StatusConta.PENDENTE); // Ou StatusConta.PAGO dependendo do fluxo
        if (pagarHoje == null) pagarHoje = BigDecimal.ZERO;

        BigDecimal receberHoje = contaReceberRepository.sumValorByDataVencimento(hoje);
        if (receberHoje == null) receberHoje = BigDecimal.ZERO;

        // Cálculo do Saldo: (Receber + Vendido) - Pagar
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

                // --- Financeiro & Vendas (Adicionados para corrigir o Teste) ---
                .quantidadeVendasHoje(qtdVendas)
                .totalVendidoHoje(totalVendido)
                .saldoDoDia(saldoDia)
                .totalVencidoPagar(vencidoPagar)

                // --- Fiscal & Auditoria ---
                .produtosMargemCritica((long) precificacaoService.buscarProdutosComMargemCritica().size())
                .produtosSemNcmOuCest(produtoRepository.contarProdutosSemFiscal())
                .ultimasAlteracoes(auditoriaService.listarUltimasAlteracoes(5)) // Se o método existir
                .build();
    }

    // Método auxiliar para evitar NullPointer em retornos de SUM
    private BigDecimal safeBigDecimal(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}