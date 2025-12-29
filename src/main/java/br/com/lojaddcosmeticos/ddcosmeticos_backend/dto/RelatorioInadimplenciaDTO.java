package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record RelatorioInadimplenciaDTO(
        String nomeCliente,
        String cpfCliente,
        String telefone,
        BigDecimal totalDevido,
        Integer quantidadeTitulosAtrasados,
        List<TituloPendenteDTO> detalhes
) {}