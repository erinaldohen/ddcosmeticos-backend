package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FluxoCaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ProdutoRepository produtoRepository;

    public DashboardResumoDTO obterResumoExecutivo() {
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioDia = hoje.atStartOfDay();
        LocalDateTime fimDia = hoje.atTime(LocalTime.MAX);

        // 1. Cálculos de Vendas
        BigDecimal totalVendidoHoje = vendaRepository.somarVendasPorPeriodo(inicioDia, fimDia);

        // CORREÇÃO NA LINHA 32: Alterado de countByDataVendaBetween para contarVendasPorPeriodo
        Long quantidadeVendasHoje = vendaRepository.contarVendasPorPeriodo(inicioDia, fimDia);

        BigDecimal ticketMedio = BigDecimal.ZERO;

        if (quantidadeVendasHoje > 0) {
            ticketMedio = totalVendidoHoje.divide(new BigDecimal(quantidadeVendasHoje), 2, RoundingMode.HALF_UP);
        }

        // 2. Cálculos Financeiros
        BigDecimal aPagarHoje = contaPagarRepository.somarAPagarPorData(hoje);
        BigDecimal aReceberHoje = contaReceberRepository.somarAReceberPorData(hoje);
        BigDecimal saldoDoDia = aReceberHoje.subtract(aPagarHoje);

        // 3. Alertas
        BigDecimal totalVencidoPagar = contaPagarRepository.somarTotalAtrasado(hoje);
        Long produtosAbaixoMinimo = produtoRepository.contarProdutosAbaixoDoMinimo();

        // 4. Projeção Semanal utilizando os Records DTO
        List<FluxoCaixaDiarioDTO> projecao = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate dataAnalise = hoje.plusDays(i);
            BigDecimal receber = contaReceberRepository.somarAReceberPorData(dataAnalise);
            BigDecimal pagar = contaPagarRepository.somarAPagarPorData(dataAnalise);

            projecao.add(FluxoCaixaDiarioDTO.builder()
                    .data(dataAnalise)
                    .aReceber(receber)
                    .aPagar(pagar)
                    .saldoPrevisto(receber.subtract(pagar))
                    .build());
        }

        // RETORNO FINAL COM BUILDER (Imutável)
        return DashboardResumoDTO.builder()
                .totalVendidoHoje(totalVendidoHoje)
                .quantidadeVendasHoje(quantidadeVendasHoje)
                .ticketMedioHoje(ticketMedio)
                .aPagarHoje(aPagarHoje)
                .aReceberHoje(aReceberHoje)
                .saldoDoDia(saldoDoDia)
                .totalVencidoPagar(totalVencidoPagar)
                .produtosAbaixoMinimo(produtosAbaixoMinimo)
                .projecaoSemanal(projecao)
                .build();
    }
}