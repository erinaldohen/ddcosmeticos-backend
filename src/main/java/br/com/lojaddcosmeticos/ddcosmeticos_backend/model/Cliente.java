package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Formula;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

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

    // 🔥 Tornamos explicito que o documento PODE ser nulo
    @Column(unique = true, length = 14, nullable = true)
    @ToString.Include
    private String documento;

    @Column(length = 10)
    private String tipoPessoa;

    @Column(nullable = false, length = 150)
    @ToString.Include
    private String nome;

    @Column(length = 150)
    private String nomeFantasia;

    @Column(length = 50)
    private String inscricaoEstadual;

    // O telefone torna-se a chave de contacto principal
    @Column(length = 20, unique = true, nullable = true)
    private String telefone;

    @Column(length = 10)
    private String cep;

    @Column(length = 150)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(name = "limite_credito", precision = 15, scale = 2)
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @NotAudited
    @Formula("(SELECT COALESCE(SUM(v.valor_total), 0) FROM tb_venda v WHERE v.id_cliente = id AND v.status_nfce != 'CANCELADA')")
    private BigDecimal totalGasto;

    @Column(name = "data_cadastro", updatable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    private boolean ativo = true;

    @PrePersist
    @PreUpdate
    public void preSalvar() {
        // 1. Tratamento do Telefone (Chave Principal B2C)
        if (this.telefone != null && !this.telefone.isBlank()) {
            this.telefone = this.telefone.replaceAll("\\D", "");
            if (this.telefone.isEmpty()) this.telefone = null;
        } else {
            this.telefone = null;
        }

        // 2. Tratamento do Documento (Pode ser Null)
        if (this.documento != null && !this.documento.isBlank()) {
            this.documento = this.documento.replaceAll("\\D", "");
            if (this.documento.isEmpty()) {
                this.documento = null;
            }
        } else {
            this.documento = null;
        }

        // 3. Classificação do Tipo de Pessoa (A regra de Ouro)
        // Só é JURIDICA se o documento existir e tiver exatamente 14 dígitos (CNPJ).
        // Qualquer outro cenário (apenas telefone, ou CPF de 11) é FISICA.
        if (this.documento != null && this.documento.length() == 14) {
            this.tipoPessoa = "JURIDICA";
        } else {
            this.tipoPessoa = "FISICA";
        }

        // 4. Tratamento do CEP
        if (this.cep != null && !this.cep.isBlank()) {
            this.cep = this.cep.replaceAll("\\D", "");
        }
    }
}