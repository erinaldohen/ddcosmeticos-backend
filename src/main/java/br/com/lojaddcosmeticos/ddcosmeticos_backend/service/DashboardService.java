package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class DashboardService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;

    @Transactional(readOnly = true)
    public DashboardResponseDTO obterResumo() {
        LocalDate hoje = LocalDate.now();
        LocalDate daquiSeteDias = hoje.plusDays(7);

        // 1. Faturamento Hoje (Null-Safe)
        LocalDateTime inicioDia = hoje.atStartOfDay();
        LocalDateTime fimDia = hoje.atTime(LocalTime.MAX);

        BigDecimal fatHoje = vendaRepository.somarVendasNoPeriodo(inicioDia, fimDia);
        BigDecimal faturamentoHoje = (fatHoje != null) ? fatHoje : BigDecimal.ZERO;
        long vendasHoje = vendaRepository.contarVendasNoPeriodo(inicioDia, fimDia);

        // 2. Projeções Financeiras de 7 dias (Consultas de Período Únicas)
        BigDecimal recebiveis = contaReceberRepository.somarRecebiveisNoPeriodo(hoje, daquiSeteDias);
        recebiveis = (recebiveis != null) ? recebiveis : BigDecimal.ZERO;

        BigDecimal pagamentos = contaPagarRepository.somarPagamentosNoPeriodo(hoje, daquiSeteDias);
        pagamentos = (pagamentos != null) ? pagamentos : BigDecimal.ZERO;

        // 3. Alerta de Stock
        long produtosCriticos = produtoRepository.contarProdutosAbaixoDoMinimo();

        return new DashboardResponseDTO(
                faturamentoHoje,
                vendasHoje,
                recebiveis,
                pagamentos,
                produtosCriticos,
                recebiveis.subtract(pagamentos)
        );
    }
}