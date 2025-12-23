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

    private BigDecimal custoAntigo;
    private BigDecimal custoNovo;

    private BigDecimal precoVendaAtual;
    private BigDecimal precoVendaSugerido;

    private BigDecimal margemAtual;
    private BigDecimal margemProjetada;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_precificacao")
    private StatusPrecificacao statusPrecificacao; // PENDENTE, APROVADO, REJEITADO

    private LocalDateTime dataGeracao = LocalDateTime.now();

    @Column(columnDefinition = "TEXT")
    private String motivo;
}