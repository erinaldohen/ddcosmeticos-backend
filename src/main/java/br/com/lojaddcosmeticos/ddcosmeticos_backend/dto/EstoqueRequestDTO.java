package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.FormaPagamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate; // Importante

@Data
public class EstoqueRequestDTO {

    @NotBlank(message = "Código de barras obrigatório")
    private String codigoBarras;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal quantidade;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal precoCusto;

    // --- CAMPOS NOVOS PARA FINANCEIRO E CUSTO REAL ---
    private BigDecimal valorImpostosAdicionais; // Frete + ST
    private LocalDate dataVencimentoBoleto;

    private String numeroNotaFiscal;
    private String fornecedorCnpj;

    // --- NOVOS CAMPOS PARA PAGAMENTO ---
    private FormaPagamento formaPagamento; // PIX, CREDITO, ETC
    private Integer quantidadeParcelas;    // Ex: 1, 3, 12... (Se nulo, assume 1)

}