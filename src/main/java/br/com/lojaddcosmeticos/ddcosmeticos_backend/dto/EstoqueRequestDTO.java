package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EstoqueRequestDTO {

    @NotBlank(message = "O código de barras é obrigatório")
    private String codigoBarras;

    // Agora suporta ID ou CNPJ
    private Long fornecedorId;
    private String fornecedorCnpj;

    @NotNull(message = "A quantidade é obrigatória")
    @DecimalMin(value = "0.0001", message = "Quantidade deve ser maior que zero")
    private BigDecimal quantidade;

    @NotNull(message = "O preço de custo é obrigatório")
    @DecimalMin(value = "0.01", message = "Preço de custo inválido")
    private BigDecimal precoCusto;

    // Dados Fiscais / Rastreabilidade
    private String numeroNotaFiscal;
    private String numeroLote;
    private LocalDate dataValidade;
    private LocalDate dataFabricacao;

    // Dados Financeiros
    private FormaDePagamento formaPagamento;
    private Integer quantidadeParcelas;
    private LocalDate dataVencimentoBoleto;
}