package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.util.List;

public record EstornoRequestDTO(
        Long vendaId,
        String motivo,
        List<ItemEstornoDTO> itensParaDevolver // Lista para devolução parcial
) {}