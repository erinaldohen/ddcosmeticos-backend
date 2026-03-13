package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class RelatorioComissaoDTO {
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private BigDecimal totalVendidoGeral = BigDecimal.ZERO;
    private BigDecimal totalComissoesGeral = BigDecimal.ZERO;
    private List<ComissaoVendedorDTO> vendedores = new ArrayList<>();
}