package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Table(name = "cliente")
public class Cliente implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // CPF ou CNPJ (sem formatação, apenas números)
    @Column(nullable = false, unique = true, length = 14)
    private String documento;

    @Column(nullable = false)
    private String nome; // Razão Social

    private String nomeFantasia; // Novo

    // Importante para NFe: Se for PJ, geralmente tem IE. Se for PF, é null ou "ISENTO".
    private String inscricaoEstadual;

    @Column(length = 10)
    private String tipoPessoa; // "FISICA" ou "JURIDICA"

    private String telefone;
    private String endereco;

    @Column(name = "limite_credito")
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    @Column(name = "data_cadastro")
    private LocalDateTime dataCadastro = LocalDateTime.now();

    private boolean ativo = true;

    // --- Lógica Automática ---
    @PrePersist
    @PreUpdate
    public void preSalvar() {
        if (this.documento != null) {
            // Remove pontos e traços para salvar limpo
            this.documento = this.documento.replaceAll("\\D", "");

            // Define tipo automaticamente
            this.tipoPessoa = this.documento.length() > 11 ? "JURIDICA" : "FISICA";
        }
    }
}