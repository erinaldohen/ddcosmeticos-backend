// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/EntradaNFRequestDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class EntradaNFRequestDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "O número da nota é obrigatório.")
    private String numeroNota;

    @NotBlank(message = "A chave de acesso é obrigatória.")
    // Padrão de 44 dígitos (chave de NF)
    @Pattern(regexp = "^[0-9]{44}$", message = "A chave de acesso deve conter 44 dígitos numéricos.")
    private String chaveAcesso;

    @NotBlank(message = "O CNPJ/CPF do fornecedor é obrigatório.")
    private String cnpjCpfFornecedor;

    @NotBlank(message = "A matrícula do operador é obrigatória.")
    private String matriculaOperador;

    @NotEmpty(message = "A lista de itens não pode ser vazia.")
    @Valid // Valida cada item dentro da lista
    private List<ItemEntradaDTO> itens;
}