package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_venda", nullable = false)
    private LocalDateTime dataVenda = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operador_id", nullable = false)
    private Usuario operador;

    @Column(name = "valor_total", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorTotal; // Soma bruta dos itens

    @Column(name = "desconto_global", precision = 10, scale = 2, nullable = false)
    private BigDecimal desconto;

    @Column(name = "valor_liquido", precision = 10, scale = 2, nullable = false)
    private BigDecimal valorLiquido; // Total - Desconto

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();

    /**
     * Persistência do XML assinado da NFC-e.
     * Armazena o documento fiscal jurídico para fins de auditoria/fiscalização (5 anos).
     */
    @Lob
    @Column(name = "xml_nfce", columnDefinition = "LONGTEXT")
    private String xmlNfce;

    /**
     * NOVO CAMPO: Controla o ciclo de vida fiscal da venda.
     * Valores possíveis:
     * - "NAO_EMITIDA" (Venda comum ou sem itens fiscais)
     * - "PRONTA_PARA_EMISSAO" (Tem itens fiscais, aguardando envio)
     * - "EMITIDA" (Sucesso na SEFAZ)
     * - "PENDENTE_ANALISE_GERENTE" (Bloqueada por estoque negativo/auditoria)
     */
    @Column(name = "status_fiscal", length = 50)
    private String statusFiscal = "NAO_EMITIDA";
}