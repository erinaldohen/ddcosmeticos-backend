package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class PedidoCompra implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataCriacao = LocalDateTime.now();

    private String fornecedorNome;

    // Novidade: Seleção de Estados para cálculo automático
    @Column(length = 2)
    private String ufOrigem; // Ex: SP, RJ, BA

    @Column(length = 2)
    private String ufDestino; // Ex: PE (Padrão)

    private BigDecimal totalProdutos;
    private BigDecimal totalImpostosEstimados; // O valor da "Barreira"
    private BigDecimal totalFinal; // Custo Real (Produtos + Impostos)

    @Enumerated(EnumType.STRING)
    private StatusPedido status; // EM_COTACAO, APROVADO, CANCELADO

    @OneToMany(mappedBy = "pedidoCompra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedidoCompra> itens = new ArrayList<>();

    public enum StatusPedido {
        EM_COTACAO, APROVADO, CANCELADO, CONCLUIDO
    }
}