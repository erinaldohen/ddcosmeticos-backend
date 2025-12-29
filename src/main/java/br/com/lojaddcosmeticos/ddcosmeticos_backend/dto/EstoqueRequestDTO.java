package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EstoqueRequestDTO {

    @NotNull(message = "O código de barras é obrigatório")
    private String codigoBarras;

    @NotNull @Positive
    private BigDecimal quantidade;

    @NotNull @Positive
    private BigDecimal precoCusto;

    private String numeroNotaFiscal;
    private String fornecedorCnpj;

    // --- NOVOS CAMPOS PARA RASTREABILIDADE ---
    private String numeroLote;
    private LocalDate dataValidade;

    // Dados para gerar conta a pagar
    private FormaDePagamento formaPagamento;
    private Integer quantidadeParcelas;
    private LocalDate dataVencimentoBoleto;
}