package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.br.CNPJ;

public record FornecedorDTO(
        Long id,
        @NotBlank(message = "Razão Social é obrigatória") String razaoSocial,
        @NotBlank(message = "Nome Fantasia é obrigatório") String nomeFantasia,
        @NotBlank(message = "CNPJ é obrigatório") @CNPJ(message = "CNPJ inválido") String cnpj, // Validação automática
        String inscricaoEstadual,
        @Email(message = "E-mail inválido") String email,
        String telefone,
        String contato, // Nome da pessoa de contato
        String cep,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        boolean ativo
) {}