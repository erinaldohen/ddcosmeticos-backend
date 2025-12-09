// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/model/Venda.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entidade que representa uma Venda/Transação no Ponto de Venda (PDV).
 * Mapeia para a tabela 'venda' no banco de dados.
 */
@Data
@Entity
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Carimbo de data e hora em que a venda foi registrada.
     */
    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda = LocalDateTime.now();

    /**
     * Valor total bruto da venda (soma dos itens sem descontos aplicados à transação).
     */
    @Column(name = "valor_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorTotal;

    /**
     * Desconto aplicado sobre o valor total da transação.
     */
    @Column(name = "desconto", precision = 10, scale = 2, nullable = false)
    private BigDecimal desconto = BigDecimal.ZERO;

    /**
     * Valor final da venda, após a aplicação de descontos (Valor Total - Desconto).
     */
    @Column(name = "valor_liquido", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorLiquido;

    /**
     * Lista de produtos e suas quantidades contidos nesta venda (Itens de Venda).
     * CascadeType.ALL: Operações como persist, merge, remove se propagarão.
     * orphanRemoval=true: Remove ItemVenda órfão quando desassociado da Venda.
     */
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens;
}