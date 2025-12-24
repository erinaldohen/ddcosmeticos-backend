package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "lote_produto")
public class LoteProduto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numeroLote;

    @Column(nullable = false)
    private LocalDate dataValidade;

    @Column(nullable = false)
    private Integer quantidadeAtual;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    public boolean estaVencido() {
        return LocalDate.now().isAfter(dataValidade);
    }

    public boolean venceEmBreve(int dias) {
        return !estaVencido() && LocalDate.now().plusDays(dias).isAfter(dataValidade);
    }
}