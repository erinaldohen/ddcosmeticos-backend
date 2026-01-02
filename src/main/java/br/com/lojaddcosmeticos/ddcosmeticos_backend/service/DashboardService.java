package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FluxoCaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import jakarta.persistence.Tuple;
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

    private static final List<StatusFiscal> STATUS_IGNORADOS = List.of(
            StatusFiscal.CANCELADA, StatusFiscal.ORCAMENTO, StatusFiscal.ERRO_EMISSAO
    );

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumo() {
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioDia = hoje.atStartOfDay();
        LocalDateTime fimDia = hoje.atTime(LocalTime.MAX);

        List<Tuple> resultadosHoje = vendaRepository.relatorioVendasPorDia(inicioDia, fimDia, STATUS_IGNORADOS);

        BigDecimal totalVendidoHoje = BigDecimal.ZERO;
        Long qtdVendasHoje = 0L;

        if (!resultadosHoje.isEmpty()) {
            Tuple tuple = resultadosHoje.get(0);
            totalVendidoHoje = tuple.get(1) != null ? new BigDecimal(tuple.get(1).toString()) : BigDecimal.ZERO;
            qtdVendasHoje = tuple.get(2) != null ? ((Number) tuple.get(2)).longValue() : 0L;
        }

        BigDecimal aPagarHoje = orZero(contaPagarRepository.somarPagamentosNoPeriodo(hoje, hoje));
        BigDecimal aReceberHoje = orZero(contaReceberRepository.somarRecebiveisNoPeriodo(hoje, hoje));

        List<FluxoCaixaDiarioDTO> projecao = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate data = hoje.plusDays(i);
            BigDecimal receber = orZero(contaReceberRepository.somarRecebiveisNoPeriodo(data, data));
            BigDecimal pagar = orZero(contaPagarRepository.somarPagamentosNoPeriodo(data, data));
            projecao.add(new FluxoCaixaDiarioDTO(data, receber, pagar, receber.subtract(pagar)));
        }

        return DashboardResumoDTO.builder()
                .totalVendidoHoje(totalVendidoHoje)
                .quantidadeVendasHoje(qtdVendasHoje)
                .ticketMedioHoje(qtdVendasHoje > 0 ? totalVendidoHoje.divide(BigDecimal.valueOf(qtdVendasHoje), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .aPagarHoje(aPagarHoje)
                .aReceberHoje(aReceberHoje)
                .saldoDoDia(totalVendidoHoje.add(aReceberHoje).subtract(aPagarHoje))
                .totalVencidoPagar(orZero(contaPagarRepository.somarTotalVencido(hoje)))
                .produtosAbaixoMinimo(produtoRepository.contarProdutosAbaixoDoMinimo())
                .projecaoSemanal(projecao)
                .build();
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}