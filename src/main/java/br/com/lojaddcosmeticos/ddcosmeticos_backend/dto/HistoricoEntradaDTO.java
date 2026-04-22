package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HistoricoEntradaDTO(
        String numeroNota,
        String serieNota,
        String chaveAcesso, // ⬅️ Essencial para a DANFE
        LocalDateTime dataEntrada, // ⬅️ O nome exato para a Data
        String fornecedorNome,
        String fornecedorCnpj,
        Long qtdItens, // ⬅️ O nome exato para a Quantidade
        BigDecimal valorTotal
) {}