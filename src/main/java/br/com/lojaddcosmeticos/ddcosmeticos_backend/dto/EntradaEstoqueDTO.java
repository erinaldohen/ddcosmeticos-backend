package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import java.time.LocalDate;
import java.util.List;

public class EntradaEstoqueDTO {

    private Long fornecedorId;
    private String numeroDocumento;
    private LocalDate dataEntrada;
    private List<ItemEntradaDTO> itens;

    // --- NOVOS CAMPOS PARA O FINANCEIRO ---
    private Integer quantidadeParcelas; // Ex: 1, 2, 3...
    private FormaDePagamento formaPagamento; // Ex: BOLETO, PIX, DINHEIRO
    private LocalDate dataVencimento; // Data da 1ª parcela

    // Getters e Setters dos novos campos
    public Integer getQuantidadeParcelas() { return quantidadeParcelas; }
    public void setQuantidadeParcelas(Integer quantidadeParcelas) { this.quantidadeParcelas = quantidadeParcelas; }

    public FormaDePagamento getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(FormaDePagamento formaPagamento) { this.formaPagamento = formaPagamento; }

    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }

    // (Mantenha os getters/setters antigos aqui...)
    public Long getFornecedorId() { return fornecedorId; }
    public void setFornecedorId(Long fornecedorId) { this.fornecedorId = fornecedorId; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }
    public LocalDate getDataEntrada() { return dataEntrada; }
    public void setDataEntrada(LocalDate dataEntrada) { this.dataEntrada = dataEntrada; }
    public List<ItemEntradaDTO> getItens() { return itens; }
    public void setItens(List<ItemEntradaDTO> itens) { this.itens = itens; }
}