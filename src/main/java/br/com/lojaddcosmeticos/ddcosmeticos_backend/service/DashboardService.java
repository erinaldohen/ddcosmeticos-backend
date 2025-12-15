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
        DashboardResumoDTO dto = new DashboardResumoDTO();
        LocalDate hoje = LocalDate.now();

        // 1. DADOS DE VENDAS (HOJE)
        LocalDateTime inicioDia = hoje.atStartOfDay();
        LocalDateTime fimDia = hoje.atTime(LocalTime.MAX);

        dto.setTotalVendidoHoje(vendaRepository.somarVendasPorPeriodo(inicioDia, fimDia));
        dto.setQuantidadeVendasHoje(vendaRepository.contarVendasPorPeriodo(inicioDia, fimDia));

        if (dto.getQuantidadeVendasHoje() > 0) {
            dto.setTicketMedioHoje(dto.getTotalVendidoHoje()
                    .divide(new BigDecimal(dto.getQuantidadeVendasHoje()), 2, RoundingMode.HALF_UP));
        } else {
            dto.setTicketMedioHoje(BigDecimal.ZERO);
        }

        // 2. FINANCEIRO IMEDIATO (HOJE)
        dto.setAPagarHoje(contaPagarRepository.somarAPagarPorData(hoje));
        // Nota: Para receber hoje, consideramos o que estava previsto para vencer hoje (D+1 de ontem)
        dto.setAReceberHoje(contaReceberRepository.somarAReceberPorData(hoje));

        dto.setSaldoDoDia(dto.getAReceberHoje().subtract(dto.getAPagarHoje()));

        // 3. ALERTAS
        dto.setTotalVencidoPagar(contaPagarRepository.somarTotalAtrasado(hoje));
        dto.setProdutosAbaixoMinimo(produtoRepository.contarProdutosAbaixoDoMinimo());

        // 4. PROJEÇÃO SEMANAL (Gráfico)
        List<FluxoCaixaDiarioDTO> projecao = new ArrayList<>();

        // Loop para os próximos 7 dias
        for (int i = 0; i < 7; i++) {
            LocalDate dataAnalise = hoje.plusDays(i);

            BigDecimal receber = contaReceberRepository.somarAReceberPorData(dataAnalise);
            BigDecimal pagar = contaPagarRepository.somarAPagarPorData(dataAnalise);
            BigDecimal saldo = receber.subtract(pagar);

            projecao.add(new FluxoCaixaDiarioDTO(dataAnalise, receber, pagar, saldo));
        }
        dto.setProjecaoSemanal(projecao);

        return dto;
    }
}