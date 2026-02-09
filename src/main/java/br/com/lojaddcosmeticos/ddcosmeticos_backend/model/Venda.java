package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Audited
@Table(name = "tb_venda", indexes = {
        @Index(name = "idx_venda_data", columnList = "dataVenda"),
        @Index(name = "idx_venda_cliente", columnList = "id_cliente")
}) // DBA: Adicionados índices para relatórios rápidos
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVenda; // Mantido idVenda pois mudar agora quebraria muito código

    @ManyToOne(fetch = FetchType.LAZY) // DBA: Performance (Carrega só se pedir)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caixa")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED) // Backend: Corrige erro do Envers
    private CaixaDiario caixa;

    private LocalDateTime dataVenda;
    private BigDecimal valorTotal;
    private BigDecimal descontoTotal;

    @Column(length = 100)
    private String clienteNome;

    @Column(length = 20)
    private String clienteDocumento;

    @Enumerated(EnumType.STRING)
    private FormaDePagamento formaDePagamento;

    private Integer quantidadeParcelas;

    // DBA: Mudado para LAZY. Onde precisar dos itens, usaremos JOIN FETCH no Repository.
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ItemVenda> itens = new ArrayList<>();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<PagamentoVenda> pagamentos = new ArrayList<>();

    private BigDecimal valorIbs;
    private BigDecimal valorCbs;
    private BigDecimal valorIs;
    private BigDecimal valorLiquido;

    @Enumerated(EnumType.STRING)
    private StatusFiscal statusNfce;

    @Column(length = 50)
    private String chaveAcessoNfce;

    @Column(length = 20)
    private String protocoloAutorizacao;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String xmlNota;

    @Column(columnDefinition = "TEXT")
    private String mensagemRejeicao;

    private LocalDateTime dataAutorizacao;
    private String motivoDoCancelamento;
    private String observacao;
}