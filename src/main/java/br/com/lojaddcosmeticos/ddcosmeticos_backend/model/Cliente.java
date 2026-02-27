package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Audited
@Table(name = "cliente")
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Regra de ouro do JPA
@ToString(onlyExplicitlyIncluded = true)
public class Cliente implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // CPF ou CNPJ (sem formatação, apenas números)
    @Column(nullable = false, unique = true, length = 14)
    @ToString.Include
    private String documento;

    @Column(nullable = false, length = 150)
    @ToString.Include
    private String nome; // Razão Social / Nome completo

    @Column(length = 150)
    private String nomeFantasia; // Novo

    // Importante para NFe: Se for PJ, geralmente tem IE. Se for PF, é null ou "ISENTO".
    @Column(length = 50)
    private String inscricaoEstadual;

    @Column(length = 10)
    private String tipoPessoa; // "FISICA" ou "JURIDICA"

    @Column(length = 20)
    private String telefone;

    @Column(length = 255)
    private String endereco;

    // DBA: Especificar a precisão no Postgres é crucial para colunas de moeda
    @Column(name = "limite_credito", precision = 15, scale = 2)
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    // DBA: updatable = false protege a data de criação contra alterações futuras
    @Column(name = "data_cadastro", updatable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    private boolean ativo = true;

    // --- Lógica Automática ---
    @PrePersist
    @PreUpdate
    public void preSalvar() {
        if (this.documento != null) {
            // Remove pontos e traços para salvar limpo no banco
            this.documento = this.documento.replaceAll("\\D", "");

            // Define tipo automaticamente baseado na quantidade de dígitos
            this.tipoPessoa = this.documento.length() > 11 ? "JURIDICA" : "FISICA";
        }
    }
}