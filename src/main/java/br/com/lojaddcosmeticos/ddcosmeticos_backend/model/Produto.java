package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction; // <--- NOVA ANOTAÇÃO (Hibernate 7)

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "produto")
// Soft Delete no Hibernate 7+:
@SQLDelete(sql = "UPDATE produto SET ativo = false WHERE id = ?")
@SQLRestriction("ativo = true") // <--- Substitui o @Where
public class Produto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras", unique = true, nullable = false)
    private String codigoBarras;

    @Column(nullable = false)
    private String descricao;

    @Column(name = "preco_custo_inicial", precision = 10, scale = 4)
    private BigDecimal precoCustoInicial;

    @Column(name = "preco_medio_ponderado", precision = 10, scale = 4)
    private BigDecimal precoMedioPonderado; // O Custo Real (PMP)

    @Column(name = "preco_venda", precision = 10, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "quantidade_estoque", precision = 10, scale = 3)
    private BigDecimal quantidadeEmEstoque;

    @Column(name = "estoque_minimo")
    private BigDecimal estoqueMinimo;

    @Column(name = "possui_nf_entrada", nullable = false)
    private boolean possuiNfEntrada = false; // Define se produto tem origem fiscal

    @Column(length = 10)
    private String ncm; // Ex: 33051000

    @Column(name = "is_monofasico")
    private boolean isMonofasico; // Se true, avisa o contador para abater PIS/COFINS

    private String cest;
    private String origem;

    // Campo novo para Soft Delete
    @Column(nullable = false)
    private boolean ativo = true;
}