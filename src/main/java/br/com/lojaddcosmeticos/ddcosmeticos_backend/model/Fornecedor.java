package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "fornecedor")
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razao_social", nullable = false)
    private String razaoSocial;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    // --- CORREÇÃO AQUI ---
    // Mapeia o atributo 'cpfOuCnpj' para a coluna exata 'cnpj_cpf' do banco
    @Column(name = "cnpj_cpf", unique = true, nullable = false)
    private String cpfOuCnpj;
    private String tipoPessoa; // "FISICA" ou "JURIDICA"

    private String telefone;
    private String email;

    private boolean ativo = true;
}