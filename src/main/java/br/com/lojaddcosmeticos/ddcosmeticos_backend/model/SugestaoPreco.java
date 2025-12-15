package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
public class SugestaoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    // Dados para comparação
    private BigDecimal custoAntigo;
    private BigDecimal custoNovo; // O novo custo que gerou o alerta

    private BigDecimal precoVendaAtual;
    private BigDecimal precoVendaSugerido;

    // Dados de Inteligência
    private BigDecimal margemAtual;     // Margem (%) se mantiver o preço atual
    private BigDecimal margemProjetada; // Margem (%) se aceitar a sugestão

    @Column(length = 500)
    private String motivo; // Ex: "Custo subiu 20%"

    private LocalDateTime dataGeracao = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    private StatusSugestao status; // PENDENTE, APROVADO, REJEITADO

    public enum StatusSugestao {
        PENDENTE,
        APROVADO,
        REJEITADO
    }
}