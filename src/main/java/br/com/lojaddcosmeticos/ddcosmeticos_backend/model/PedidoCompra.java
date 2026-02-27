package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPedido;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Audited
@Table(name = "pedido_compra") // Padronização do nome da tabela no Postgres
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Regra de Ouro do JPA
@ToString(onlyExplicitlyIncluded = true) // Proteção contra Logs N+1
public class PedidoCompra implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // updatable = false protege a data original de criação
    @Column(name = "data_criacao", updatable = false, nullable = false)
    @ToString.Include
    private LocalDateTime dataCriacao = LocalDateTime.now();

    // DBA FIX: Alterado para LAZY. Nunca usar EAGER padrão do @ManyToOne
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    @Column(name = "fornecedor_nome", length = 150)
    @ToString.Include
    private String fornecedorNome; // Histórico (snapshot)

    // Seleção de Estados para cálculo automático
    @Column(name = "uf_origem", length = 2)
    @ToString.Include
    private String ufOrigem; // Ex: SP, RJ, BA

    @Column(name = "uf_destino", length = 2)
    private String ufDestino; // Ex: PE (Padrão)

    // DBA FIX: Precisão essencial para o cálculo da "Barreira Fiscal" no Postgres
    @Column(name = "total_produtos", precision = 15, scale = 2)
    private BigDecimal totalProdutos;

    @Column(name = "total_impostos_estimados", precision = 15, scale = 2)
    private BigDecimal totalImpostosEstimados; // O valor da "Barreira"

    @Column(name = "total_final", precision = 15, scale = 2)
    @ToString.Include
    private BigDecimal totalFinal; // Custo Real (Produtos + Impostos)

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @ToString.Include
    private StatusPedido status; // EM_COTACAO, APROVADO, CANCELADO

    // A lista já é LAZY por padrão no @OneToMany, o que a salva de quebrar é a remoção do @Data acima
    @OneToMany(mappedBy = "pedidoCompra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedidoCompra> itens = new ArrayList<>();

}