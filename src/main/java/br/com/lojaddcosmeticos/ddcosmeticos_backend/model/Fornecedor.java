package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.io.Serializable;

@Data
@Entity
@Audited
@Table(name = "fornecedor", indexes = {
        @Index(name = "idx_fornecedor_nome", columnList = "nome_fantasia")
})
public class Fornecedor implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "A Razão Social é obrigatória")
    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    // A Regex abaixo permite APENAS números.
    @NotBlank(message = "O CPF/CNPJ é obrigatório")
    @Pattern(regexp = "(^\\d{11}$)|(^\\d{14}$)", message = "O documento deve conter exatamente 11 (CPF) ou 14 (CNPJ) dígitos, sem pontos ou traços.")
    @Column(name = "cnpj_cpf", unique = true, nullable = false, length = 14)
    private String cpfOuCnpj;

    @Column(name = "inscricao_estadual")
    private String inscricaoEstadual; // <--- CAMPO NOVO (Necessário para o XML)

    @Pattern(regexp = "FISICA|JURIDICA", message = "O tipo deve ser FISICA ou JURIDICA")
    private String tipoPessoa;

    private String telefone;

    @Email(message = "Formato de e-mail inválido")
    private String email;

    // --- NOVOS CAMPOS DE ENDEREÇO (Necessários para o XML e API) ---
    private String cep;
    private String logradouro;
    private String numero;
    private String bairro;
    private String cidade;

    @Column(length = 2)
    private String estado; // UF
    // ---------------------------------------------------------------

    private boolean ativo = true;
}