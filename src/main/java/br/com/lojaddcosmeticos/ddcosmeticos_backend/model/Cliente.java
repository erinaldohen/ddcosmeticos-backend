package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Cliente implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // 🔥 MUDANÇA: nullable = false foi removido. Agora CPF é opcional.
    @Column(unique = true, length = 14)
    @ToString.Include
    private String documento;

    @Column(nullable = false, length = 150)
    @ToString.Include
    private String nome;

    @Column(length = 150)
    private String nomeFantasia;

    @Column(length = 50)
    private String inscricaoEstadual;

    @Column(length = 10)
    private String tipoPessoa;

    // Telefone passa a ser chave importantíssima para PF
    @Column(length = 20, unique = true)
    private String telefone;

    @Column(length = 255)
    private String endereco;

    @Column(name = "limite_credito", precision = 15, scale = 2)
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @Column(name = "data_cadastro", updatable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    private boolean ativo = true;

    @PrePersist
    @PreUpdate
    public void preSalvar() {
        if (this.documento != null && !this.documento.isBlank()) {
            this.documento = this.documento.replaceAll("\\D", "");
            this.tipoPessoa = this.documento.length() > 11 ? "JURIDICA" : "FISICA";
        } else {
            this.documento = null;
            this.tipoPessoa = "FISICA"; // Se não tem doc, é consumidor final PF
        }

        if (this.telefone != null && !this.telefone.isBlank()) {
            this.telefone = this.telefone.replaceAll("\\D", ""); // Limpa o telefone
        }
    }
}