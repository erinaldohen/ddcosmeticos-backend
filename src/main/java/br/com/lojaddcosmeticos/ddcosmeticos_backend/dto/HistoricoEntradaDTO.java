package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HistoricoEntradaDTO(
        String numeroNota,
        String serieNota,
        String chaveAcesso, // ⬅️ Essencial para o React e para a DANFE
        LocalDateTime dataEntrada,
        String fornecedorNome,
        String fornecedorCnpj,
        Long qtdItens,
        BigDecimal valorTotal
) {}