package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "produto")
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras", unique = true, nullable = false)
    private String codigoBarras; // EAN

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "preco_custo_inicial", precision = 10, scale = 4)
    private BigDecimal precoCustoInicial;

    @Column(name = "preco_medio_ponderado", precision = 10, scale = 4)
    private BigDecimal precoMedioPonderado; // Será o custo principal

    @Column(name = "preco_venda", precision = 10, scale = 2)
    private BigDecimal precoVendaVarejo;

    @Column(name = "quantidade_estoque")
    private BigDecimal quantidadeEmEstoque;

    @Column(name = "estoque_minimo")
    private BigDecimal estoqueMinimo;

    @Column(name = "ncm", length = 8)
    private String ncm;

    @Column(name = "cest", length = 7)
    private String cest;

    // Campo para aplicar a regra do Duplo Comprovante
    @Column(name = "possui_nf_entrada", nullable = false)
    private Boolean possuiNfEntrada = false;

    // O campo 'Origem' do CSV será usado para sugerir o valor de 'possui_nf_entrada'
    @Column(name = "origem", length = 50)
    private String origem;

    // Requisito 4: Lote e Validade serão em tabelas separadas, mas o ID do Produto as conecta
    // ... outros campos (Marca, Categoria)
}