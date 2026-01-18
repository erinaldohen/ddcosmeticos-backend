package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.br.CNPJ;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FornecedorDTO {

    private Long id;

    @NotBlank(message = "Razão Social é obrigatória")
    private String razaoSocial;

    @NotBlank(message = "Nome Fantasia é obrigatório")
    private String nomeFantasia;

    @NotBlank(message = "CNPJ é obrigatório")
    @CNPJ(message = "CNPJ inválido")
    private String cnpj;

    private String inscricaoEstadual;

    @Email(message = "E-mail inválido")
    private String email;

    private String telefone;
    private String contato;

    // Endereço
    private String cep;
    private String logradouro;
    private String numero;
    private String bairro;
    private String cidade;
    private String uf;

    private Boolean ativo;
}