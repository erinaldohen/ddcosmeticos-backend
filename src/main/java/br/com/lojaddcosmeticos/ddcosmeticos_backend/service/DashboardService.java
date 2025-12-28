package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FluxoCaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumo() {
        LocalDate hoje = LocalDate.now();

        // 1. O Dia (Vendas)
        LocalDateTime inicioDia = hoje.atStartOfDay();
        LocalDateTime fimDia = hoje.atTime(LocalTime.MAX);

        // CORREÇÃO LINHA 34: O nome correto no repositório é 'somarVendasNoPeriodo'
        BigDecimal totalVendidoHoje = orZero(vendaRepository.somarVendasNoPeriodo(inicioDia, fimDia));

        // CORREÇÃO LINHA 35: O nome correto no repositório é 'contarVendasNoPeriodo'
        // Como retorna 'long' primitivo, não precisamos do orZeroLong aqui, mas mantemos o cast para garantir
        Long qtdVendasHoje = vendaRepository.contarVendasNoPeriodo(inicioDia, fimDia);

        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (qtdVendasHoje > 0) {
            ticketMedio = totalVendidoHoje.divide(BigDecimal.valueOf(qtdVendasHoje), 2, RoundingMode.HALF_UP);
        }

        // 2. O Financeiro (Hoje - Contas)
        BigDecimal aPagarHoje = orZero(contaPagarRepository.somarPagamentosNoPeriodo(hoje, hoje));
        BigDecimal aReceberHoje = orZero(contaReceberRepository.somarRecebiveisNoPeriodo(hoje, hoje));

        // Saldo do dia
        BigDecimal saldoDoDia = totalVendidoHoje.add(aReceberHoje).subtract(aPagarHoje);

        // 3. Alertas
        // OBS: Você precisa garantir que o método 'somarTotalVencido' e 'contarProdutosAbaixoDoMinimo' existam nos repositórios (veja passo 2 abaixo)
        BigDecimal totalVencido = orZero(contaPagarRepository.somarTotalVencido(hoje));
        Long produtosAbaixoMinimo = produtoRepository.contarProdutosAbaixoDoMinimo();

        // 4. Gráfico
        List<FluxoCaixaDiarioDTO> projecao = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate data = hoje.plusDays(i);
            BigDecimal receberDia = orZero(contaReceberRepository.somarRecebiveisNoPeriodo(data, data));
            BigDecimal pagarDia = orZero(contaPagarRepository.somarPagamentosNoPeriodo(data, data));

            projecao.add(FluxoCaixaDiarioDTO.builder()
                    .data(data)
                    .aReceber(receberDia)
                    .aPagar(pagarDia)
                    .saldoPrevisto(receberDia.subtract(pagarDia))
                    .build());
        }

        return DashboardResumoDTO.builder()
                .totalVendidoHoje(totalVendidoHoje)
                .quantidadeVendasHoje(qtdVendasHoje)
                .ticketMedioHoje(ticketMedio)
                .aPagarHoje(aPagarHoje)
                .aReceberHoje(aReceberHoje)
                .saldoDoDia(saldoDoDia)
                .totalVencidoPagar(totalVencido)
                .produtosAbaixoMinimo(produtosAbaixoMinimo)
                .projecaoSemanal(projecao)
                .build();
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}