package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
@Table(name = "produto")
// 1. O SEGREDO DO SOFT DELETE:
// Quando alguém chamar 'delete()', o Hibernate executa esse UPDATE no lugar:
@SQLDelete(sql = "UPDATE produto SET ativo = false WHERE id = ?")
// 2. O FILTRO AUTOMÁTICO:
// Toda vez que buscar produtos (findAll), ele só traz os ativos automaticamente:
@SQLRestriction("ativo = true")
public class Produto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    private String descricao;

    @Column(name = "preco_custo", precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @Column(name = "preco_venda", precision = 10, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "quantidade_estoque")
    private Integer quantidadeEstoque;

    @Column(name = "codigo_barras", unique = true)
    private String codigoBarras;

    private String categoria;

    // Campo essencial para a regra de negócio
    @Column(nullable = false)
    private boolean ativo = true;
}