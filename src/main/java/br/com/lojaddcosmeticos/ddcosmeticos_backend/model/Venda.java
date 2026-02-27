package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// DBA/Performance: Substituído @Data por Getter, Setter e Equals baseado apenas na chave primária
@Getter
@Setter
@NoArgsConstructor
@Entity
@Audited
@Table(name = "tb_venda", indexes = {
        @Index(name = "idx_venda_data", columnList = "dataVenda"),
        @Index(name = "idx_venda_cliente", columnList = "id_cliente")
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Regra de ouro do JPA
@ToString(onlyExplicitlyIncluded = true) // Impede que o log dispare N+1 queries nas listas LAZY
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long idVenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caixa")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private CaixaDiario caixa;

    @ToString.Include
    private LocalDateTime dataVenda;

    @ToString.Include
    private BigDecimal valorTotal;

    private BigDecimal descontoTotal;

    @Column(length = 100)
    private String clienteNome;

    @Column(length = 20)
    private String clienteDocumento;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private FormaDePagamento formaDePagamento;

    private Integer quantidadeParcelas;

    // Relacionamentos mantidos LAZY e blindados pelo lombok otimizado acima
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemVenda> itens = new ArrayList<>();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PagamentoVenda> pagamentos = new ArrayList<>();

    private BigDecimal valorIbs;
    private BigDecimal valorCbs;
    private BigDecimal valorIs;
    private BigDecimal valorLiquido;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private StatusFiscal statusNfce;

    @Column(length = 50)
    private String chaveAcessoNfce;

    @Column(length = 20)
    private String protocoloAutorizacao;

    // FIX POSTGRESQL: O @Lob foi removido. Apenas columnDefinition="TEXT" é necessário
    // e suficiente para que o Postgres crie um campo de texto ilimitado.
    @Column(columnDefinition = "TEXT")
    private String xmlNota;

    @Column(columnDefinition = "TEXT")
    private String mensagemRejeicao;

    private LocalDateTime dataAutorizacao;

    @Column(length = 255)
    private String motivoDoCancelamento;

    @Column(columnDefinition = "TEXT") // Transformado em texto longo caso a observação seja gigante
    private String observacao;
}