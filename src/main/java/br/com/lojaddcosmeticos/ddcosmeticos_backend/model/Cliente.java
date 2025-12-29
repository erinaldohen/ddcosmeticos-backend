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

    // ==================================================================================
    // SESSÃO 1: IDENTIFICADORES E DADOS PESSOAIS
    // ==================================================================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(nullable = false)
    private String nome;

    private String telefone;

    private String endereco;

    // ==================================================================================
    // SESSÃO 2: DADOS FINANCEIROS (FIADO)
    // ==================================================================================

    /**
     * Valor máximo que a loja aceita vender "fiado" para este cliente.
     * Ex: Se for R$ 500,00 e o cliente já dever R$ 400,00, ele só pode comprar mais R$ 100,00.
     */
    @Column(name = "limite_credito")
    private BigDecimal limiteCredito = BigDecimal.ZERO;

    // ==================================================================================
    // SESSÃO 3: METADADOS E CONTROLE
    // ==================================================================================

    @Column(name = "data_cadastro")
    private LocalDateTime dataCadastro = LocalDateTime.now();

    private boolean ativo = true;
}