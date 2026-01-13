package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoDestinatarioSplit;

import java.math.BigDecimal;

public record SplitPaymentInstructionDTO(
        TipoDestinatarioSplit destinatarioTipo, // "GOVERNO_FEDERAL", "GOVERNO_ESTADUAL", "LOJISTA"
        BigDecimal valor,
        String identificadorRecebedor // Chave Pix ou Conta do Ente Público
) {}