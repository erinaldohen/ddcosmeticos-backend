package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusSugestao; // <--- O IMPORT QUE FALTA
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sugestao_preco")
public class SugestaoPreco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(name = "custo_antigo")
    private BigDecimal custoAntigo;

    @Column(name = "custo_novo")
    private BigDecimal custoNovo;

    @Column(name = "preco_venda_atual")
    private BigDecimal precoVendaAtual;

    @Column(name = "preco_venda_sugerido")
    private BigDecimal precoVendaSugerido;

    @Column(name = "margem_atual")
    private BigDecimal margemAtual;

    @Column(name = "margem_projetada")
    private BigDecimal margemProjetada;

    @Enumerated(EnumType.STRING)
    private StatusSugestao status; // Agora ele vai reconhecer este tipo

    @Column(length = 500)
    private String motivo;

    @Column(name = "data_geracao")
    private LocalDateTime dataGeracao = LocalDateTime.now();
}