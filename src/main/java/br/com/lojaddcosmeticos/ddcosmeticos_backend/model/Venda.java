package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@NoArgsConstructor
@Table(name = "venda")
public class Venda implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // --- CORREÇÃO AQUI: (status-fiscal -> status_fiscal) ---
    @Enumerated(EnumType.STRING)
    @Column(name = "status_fiscal", nullable = false)
    private StatusFiscal statusFiscal = StatusFiscal.PENDENTE;

    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda = LocalDateTime.now();

    @Column(name = "cliente_documento")
    private String clienteDocumento;

    @Column(name = "cliente_nome")
    private String clienteNome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "total_venda", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalVenda = BigDecimal.ZERO;

    @Column(name = "desconto_total", precision = 10, scale = 2)
    private BigDecimal descontoTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false)
    private FormaDePagamento formaPagamento;

    private Integer quantidadeParcelas;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String motivoDoCancelamento;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String xmlNfce;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();

    // Métodos de compatibilidade usados pelo Service
    public void setClienteCpf(String documento) {
        this.clienteDocumento = documento;
    }

    public String getClienteCpf() {
        return this.clienteDocumento;
    }

    public void adicionarItem(ItemVenda item) {
        item.setVenda(this);
        this.itens.add(item);
    }
}