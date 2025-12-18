package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "venda")
public class Venda implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda = LocalDateTime.now();

    // AUDITORIA: Quem realizou a operação
    @Column(name = "usuario_vendedor", nullable = false)
    private String usuarioVendedor;

    // FINANCEIRO: Como foi pago (Vinculado ao seu Enum)
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false)
    private FormaPagamento formaPagamento;

    // TOTALIZAÇÃO
    @Column(name = "total_venda", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalVenda = BigDecimal.ZERO;

    @Column(name = "desconto_total", precision = 10, scale = 2)
    private BigDecimal descontoTotal = BigDecimal.ZERO;

    // CLIENTE
    @Column(name = "cliente_cpf")
    private String clienteCpf;

    @Column(name = "cliente_nome")
    private String clienteNome;

    // GESTÃO DE ESTORNOS
    private boolean cancelada = false;
    private String motivoCancelamento;

    // STATUS FISCAL
    private String statusFiscal; // "PENDENTE", "AUTORIZADO", "ERRO"

    @Lob
    @Column(columnDefinition = "TEXT")
    private String xmlNfce;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemVenda> itens = new ArrayList<>();

    public void adicionarItem(ItemVenda item) {
        itens.add(item);
        item.setVenda(this);
    }
}