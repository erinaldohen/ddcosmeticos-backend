package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record VendaResponseDTO(
        Long idVenda,
        LocalDateTime dataVenda,
        String clienteNome,      // SIM, mantemos!
        String clienteDocumento, // Novo nome
        BigDecimal valorTotal,
        BigDecimal desconto,
        Integer totalItens,
        StatusFiscal statusFiscal,
        List<String> alertas
) {}