package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoInfluenciaIA;
import java.math.BigDecimal;

public record ItemVendaRequestDTO(
        Long produtoId,               // 🔥 Alinhado com o Frontend (React)
        String codigoBarras,          // Mantido por segurança caso use em outro local
        BigDecimal quantidade,
        BigDecimal precoUnitario,     // 🔥 Agora o backend recebe o preço correto do PDV
        BigDecimal desconto,          // 🔥 Agora o backend recebe o desconto do item
        TipoInfluenciaIA influenciaIA // 🤖 NOVO: Captura o impacto da IA (DIRETA, INDIRETA, NENHUMA)
) {}