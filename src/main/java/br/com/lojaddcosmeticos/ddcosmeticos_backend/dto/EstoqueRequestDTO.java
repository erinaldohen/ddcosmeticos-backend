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
    private Long idProduto; // O ID do produto que o usuário confirmou (pode ser da sugestão ou busca manual)
    private String codigoNoFornecedor; // O código original do XML para criar o vínculo

    // --- NOVOS CAMPOS PARA AUTO-CADASTRO (IMPORTANTE) ---
    // Se o produto não existir, usaremos esses dados para criá-lo
    private String descricao;
    private String ncm;
    private String unidade;
    // ----------------------------------------------------

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