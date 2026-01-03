package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sugestao_preco")
public class SugestaoPreco implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    // Renomeado para bater com o Service (custoBase)
    private BigDecimal custoBase;

    private BigDecimal precoVendaAtual;
    private BigDecimal precoVendaSugerido;

    private BigDecimal margemAtualPercentual;
    private BigDecimal margemSugeridaPercentual;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_precificacao")
    private StatusPrecificacao statusPrecificacao;

    private LocalDateTime dataSugestao = LocalDateTime.now();
    private LocalDateTime dataAprovacao;

    @Column(columnDefinition = "TEXT")
    private String observacao;
}