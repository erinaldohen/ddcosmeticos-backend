package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import jakarta.persistence.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    @Autowired private VendaRepository vendaRepository;

    private static final List<StatusFiscal> STATUS_IGNORADOS = List.of(
            StatusFiscal.CANCELADA, StatusFiscal.ORCAMENTO, StatusFiscal.ERRO_EMISSAO
    );

    @Transactional(readOnly = true)
    public RelatorioVendasDTO gerarRelatorioVendas(LocalDate inicio, LocalDate fim) {
        if (inicio == null) inicio = LocalDate.now().minusDays(30);
        if (fim == null) fim = LocalDate.now();

        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(LocalTime.MAX);

        // 1. Evolução Diária
        List<VendaDiariaDTO> evolucao = vendaRepository.relatorioVendasPorDia(dataInicio, dataFim, STATUS_IGNORADOS).stream()
                .map(t -> new VendaDiariaDTO(
                        converterParaLocalDate(t.get(0)),
                        converterParaBigDecimal(t.get(1)),
                        ((Number) t.get(2)).longValue()
                )).collect(Collectors.toList());

        BigDecimal faturamentoTotal = evolucao.stream().map(VendaDiariaDTO::getTotalVendido).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long totalVendas = evolucao.stream().mapToLong(VendaDiariaDTO::getQuantidadeVendas).sum();

        // 2. Ranking de Produtos (Top 10)
        List<ProdutoRankingDTO> topProdutos = vendaRepository.relatorioProdutosMaisVendidos(dataInicio, dataFim, STATUS_IGNORADOS, PageRequest.of(0, 10)).stream()
                .map(t -> new ProdutoRankingDTO(
                        (String) t.get(0),
                        (String) t.get(1),
                        ((Number) t.get(2)).longValue(),
                        converterParaBigDecimal(t.get(3))
                )).collect(Collectors.toList());

        // 3. Vendas por Forma de Pagamento
        List<VendaPorPagamentoDTO> pagamentos = vendaRepository.relatorioVendasPorPagamento(dataInicio, dataFim, STATUS_IGNORADOS).stream()
                .map(t -> new VendaPorPagamentoDTO(
                        (FormaDePagamento) t.get(0),
                        converterParaBigDecimal(t.get(1)),
                        ((Number) t.get(2)).longValue()
                )).collect(Collectors.toList());

        return RelatorioVendasDTO.builder()
                .faturamentoTotal(faturamentoTotal)
                .totalVendasRealizadas(totalVendas)
                .ticketMedio(totalVendas > 0 ? faturamentoTotal.divide(BigDecimal.valueOf(totalVendas), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .evolucaoDiaria(evolucao)
                .vendasPorPagamento(pagamentos)
                .produtosMaisVendidos(topProdutos)
                .build();
    }

    /**
     * Implementação Analítica da Curva ABC
     * Classifica os produtos em A (80% do faturamento), B (15%) e C (5%)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> gerarCurvaAbc() {
        LocalDateTime inicio = LocalDateTime.now().minusYears(1); // Analisa o último ano
        LocalDateTime fim = LocalDateTime.now();

        // Pega uma amostra maior de produtos para classificar
        List<Tuple> ranking = vendaRepository.relatorioProdutosMaisVendidos(inicio, fim, STATUS_IGNORADOS, PageRequest.of(0, 100));

        BigDecimal faturamentoTotal = ranking.stream()
                .map(t -> converterParaBigDecimal(t.get(3)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> curvaAbc = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;

        for (Tuple t : ranking) {
            BigDecimal totalProduto = converterParaBigDecimal(t.get(3));
            acumulado = acumulado.add(totalProduto);

            BigDecimal percentualParticipacao = faturamentoTotal.compareTo(BigDecimal.ZERO) > 0
                    ? totalProduto.multiply(new BigDecimal("100")).divide(faturamentoTotal, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal percentualAcumulado = faturamentoTotal.compareTo(BigDecimal.ZERO) > 0
                    ? acumulado.multiply(new BigDecimal("100")).divide(faturamentoTotal, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String classe = "C";
            if (percentualAcumulado.compareTo(new BigDecimal("80")) <= 0) classe = "A";
            else if (percentualAcumulado.compareTo(new BigDecimal("95")) <= 0) classe = "B";

            Map<String, Object> item = new HashMap<>();
            item.put("nome", t.get(1));
            item.put("codigo", t.get(0));
            item.put("total", totalProduto);
            item.put("qtd", t.get(2));
            item.put("classe", classe);
            item.put("participacao", percentualParticipacao);

            curvaAbc.add(item);
        }

        return curvaAbc;
    }

    private BigDecimal converterParaBigDecimal(Object valor) {
        if (valor == null) return BigDecimal.ZERO;
        if (valor instanceof BigDecimal) return (BigDecimal) valor;
        return new BigDecimal(valor.toString());
    }

    private LocalDate converterParaLocalDate(Object valor) {
        if (valor == null) return null;
        if (valor instanceof java.sql.Date) return ((java.sql.Date) valor).toLocalDate();
        if (valor instanceof java.util.Date) return ((java.util.Date) valor).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if (valor instanceof LocalDate) return (LocalDate) valor;
        return null;
    }
}