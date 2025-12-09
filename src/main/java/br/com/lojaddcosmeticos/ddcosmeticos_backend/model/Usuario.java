// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/model/Usuario.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidade que representa o operador de caixa ou usuário do sistema.
 * Usada para auditoria (quem registrou a venda).
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "matricula", unique = true, nullable = false, length = 10)
    private String matricula; // ID único do operador no sistema

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    // Construtor auxiliar para a inicialização
    public Usuario(String nome, String matricula) {
        this.nome = nome;
        this.matricula = matricula;
    }
}