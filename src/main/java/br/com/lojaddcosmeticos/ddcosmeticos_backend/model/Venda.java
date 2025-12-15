package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataVenda = LocalDateTime.now();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();

    private BigDecimal totalVenda;
    private BigDecimal descontoTotal;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento; // Novo campo

    @ManyToOne
    private Usuario operador;

    // Campos fiscais (NF-e/NFC-e)
    @Lob
    private String xmlNfce;
    private String statusFiscal; // EMITIDA, CONTINGENCIA, ERRO
}