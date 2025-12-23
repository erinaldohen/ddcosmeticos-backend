package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EstoqueRequestDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    private String codigoBarras;
    private BigDecimal quantidade;
    private BigDecimal precoCusto;
    private String numeroNotaFiscal;
    private String fornecedorCnpj;

    private FormaDePagamento formaPagamento;

    private Integer quantidadeParcelas;
    private LocalDate dataVencimentoBoleto;

    // --- CAMPO ADICIONADO PARA CORRIGIR O ERRO ---
    private BigDecimal valorImpostosAdicionais;
}