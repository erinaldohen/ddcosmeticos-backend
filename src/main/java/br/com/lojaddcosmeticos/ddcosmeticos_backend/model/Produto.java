package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entidade Produto refatorada pela equipe de Arquitetura.
 * Inclui suporte a controle de validade, imagem para IA e flags fiscais para o cenário híbrido.
 */
@Data
@Entity
@Table(name = "produto")
@SQLDelete(sql = "UPDATE produto SET ativo = false WHERE id = ?")
@SQLRestriction("ativo = true")
public class Produto implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras", unique = true, nullable = false)
    private String codigoBarras;

    @Column(nullable = false)
    private String descricao;

    @Column(length = 10)
    private String unidade = "UN";

    private boolean ativo = true;

    // --- CAMPOS FINANCEIROS ---
    @Column(name = "preco_custo_inicial", precision = 10, scale = 4)
    private BigDecimal precoCustoInicial;

    @Column(name = "preco_medio_ponderado", precision = 10, scale = 4)
    private BigDecimal precoMedioPonderado;

    @Column(name = "preco_venda", precision = 10, scale = 2)
    private BigDecimal precoVenda;

    @Column(name = "quantidade_estoque", precision = 10, scale = 3)
    private BigDecimal quantidadeEmEstoque = BigDecimal.ZERO;

    @Column(name = "estoque_minimo", precision = 10, scale = 3)
    private BigDecimal estoqueMinimo = BigDecimal.ZERO;

    // --- CAMPOS FISCAIS (Cruciais para NFC-e em PE - CNAE 4772-5/00) ---
    @Column(length = 10)
    private String ncm;

    @Column(length = 10)
    private String cest;

    @Column(length = 1)
    private String origem = "0"; // 0-Nacional, 1-Importado

    private boolean monofasico = false;

    // Flag crucial para a regra de negócio de emissão parcial de NFC-e
    @Column(name = "possui_nf_entrada")
    private boolean possuiNfEntrada = false;

    // --- CONTROLE DE VALIDADE E LOTE (Solicitado no item 4) ---
    @Column(name = "controla_validade")
    private boolean controlaValidade = false;

    // Quantidade de dias antes do vencimento para o sistema emitir alerta
    @Column(name = "dias_alerta_vencimento")
    private Integer diasParaAlertaVencimento = 30;

    // Data de validade do lote mais próximo de vencer (atualizado pelo EstoqueService)
    @Column(name = "data_validade_proxima")
    private LocalDate dataValidadeProxima;

    // --- UI/UX & INOVAÇÃO ---
    // URL da imagem para exibição no PDV e para o algoritmo de reconhecimento visual
    @Column(name = "url_imagem", length = 500)
    private String urlImagem;
}