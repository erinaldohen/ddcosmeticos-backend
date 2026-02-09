package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

public record VendaPorPagamentoDTO (
        FormaDePagamento formaPagamento,
        BigDecimal valorTotal,
        Long quantidade){}