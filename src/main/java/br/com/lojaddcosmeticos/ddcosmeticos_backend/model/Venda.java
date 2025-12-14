package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data // O Lombok gera o getTotalVenda() automaticamente por causa deste campo
@Entity
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime dataVenda = LocalDateTime.now();

    /**
     * O campo crucial que estava faltando ou com nome diferente.
     * O Lombok vai gerar: public BigDecimal getTotalVenda()
     */
    @Column(name = "total_venda", nullable = false)
    private BigDecimal totalVenda;

    private BigDecimal descontoTotal = BigDecimal.ZERO;

    private String formaPagamento; // DINHEIRO, CARTAO, PIX

    private String statusFiscal; // PENDENTE, AUTORIZADO, CONTINGENCIA, CANCELADO

    @Lob // Indica texto grande
    @Column(columnDefinition = "LONGTEXT") // Garante que cabe o XML inteiro no MySQL
    private String xmlNfce;

    @ManyToOne
    @JoinColumn(name = "operador_id")
    private Usuario operador;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();
}