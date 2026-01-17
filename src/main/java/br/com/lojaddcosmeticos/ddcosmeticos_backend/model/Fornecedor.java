package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Data
@Entity
@Audited
@Table(name = "fornecedores")
public class Fornecedor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String razaoSocial;
    @Column(nullable = false) private String nomeFantasia;
    @Column(nullable = false, unique = true, length = 18) private String cnpj;
    private String inscricaoEstadual;

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

    private boolean ativo = true;

    @Column(updatable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();
}