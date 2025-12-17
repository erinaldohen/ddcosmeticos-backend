package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EstoqueRequestDTO {

    private String codigoBarras;
    private BigDecimal quantidade;
    private BigDecimal precoCusto;
    private String numeroNotaFiscal;
    private String fornecedorCnpj;

    private FormaPagamento formaPagamento;

    private Integer quantidadeParcelas;
    private LocalDate dataVencimentoBoleto;

    // --- CAMPO ADICIONADO PARA CORRIGIR O ERRO ---
    private BigDecimal valorImpostosAdicionais;
}