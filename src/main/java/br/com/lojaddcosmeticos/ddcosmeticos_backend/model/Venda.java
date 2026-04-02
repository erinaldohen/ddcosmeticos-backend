package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.CanalOrigem;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Audited
@Table(name = "tb_venda", indexes = {
        @Index(name = "idx_venda_data", columnList = "dataVenda"),
        @Index(name = "idx_venda_status", columnList = "statusNfce"),
        @Index(name = "idx_venda_cliente", columnList = "id_cliente")
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long idVenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(precision = 15, scale = 2)
    private BigDecimal troco = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private CanalOrigem canalOrigem = CanalOrigem.LOJA_FISICA;

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
    @Column(precision = 15, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal custoTotal = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal descontoTotal = BigDecimal.ZERO;

    // Regra de negócio nativa: Valor padrão para emissão sem identificação
    @Column(length = 100)
    private String clienteNome = "Consumidor Não Identificado";

    @Column(name = "cliente_telefone", length = 20)
    private String clienteTelefone;

    @Column(length = 20)
    private String clienteDocumento;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private FormaDePagamento formaDePagamento;

    private Integer quantidadeParcelas = 1;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ItemVenda> itens = new ArrayList<>();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PagamentoVenda> pagamentos = new ArrayList<>();

    @Column(precision = 15, scale = 2) private BigDecimal valorIbs = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2) private BigDecimal valorCbs = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2) private BigDecimal valorIs = BigDecimal.ZERO;
    @Column(precision = 15, scale = 2) private BigDecimal valorLiquido = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private StatusFiscal statusNfce;

    // =========================================================================
    // DADOS FISCAIS NFC-E (CORRIGIDOS PARA COMPATIBILIDADE COM DTO E SEFAZ)
    // =========================================================================

    @Column(length = 50)
    private String chaveAcessoNfce;

    @Column(length = 50)
    private String protocolo; // Renomeado de protocoloAutorizacao para ser lido corretamente

    @Column
    private Long numeroNfce; // Adicionado: Faltava na base de dados

    @Column
    private Integer serieNfce; // Adicionado: Faltava na base de dados

    @Column(columnDefinition = "TEXT")
    private String xmlNota;

    @Column(columnDefinition = "TEXT")
    private String mensagemRejeicao;

    private LocalDateTime dataAutorizacao;

    @Column(length = 255)
    private String motivoDoCancelamento;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    // AQUI ESTÁ A CORREÇÃO CRÍTICA:
    // Mapeamento da URL gerada pela Sefaz para persistência no banco
    @Column(columnDefinition = "TEXT")
    private String urlQrCode;

    // =========================================================================
    // MÉTODOS DE SINCRONIZAÇÃO JPA (BEST PRACTICES)
    // =========================================================================

    public void addItem(ItemVenda item) {
        this.itens.add(item);
        item.setVenda(this);
    }

    public void addPagamento(PagamentoVenda pagamento) {
        this.pagamentos.add(pagamento);
        pagamento.setVenda(this);
    }

    // =========================================================================
    // MÉTODOS DE CONVENIÊNCIA
    // =========================================================================

    public Usuario getVendedor() {
        return this.usuario;
    }

    public String getFormaPagamento() {
        return this.formaDePagamento != null ? this.formaDePagamento.name() : "";
    }

    public BigDecimal getLucroBruto() {
        BigDecimal totalSeguro = this.valorTotal != null ? this.valorTotal : BigDecimal.ZERO;
        return totalSeguro.subtract(getCustoTotal());
    }

    public BigDecimal getCustoTotal() {
        if (this.custoTotal != null && this.custoTotal.compareTo(BigDecimal.ZERO) > 0) {
            return this.custoTotal;
        }
        if (this.itens != null && !this.itens.isEmpty()) {
            return this.itens.stream()
                    .map(item -> {
                        BigDecimal custoUni = item.getCustoUnitarioHistorico() != null ? item.getCustoUnitarioHistorico() : BigDecimal.ZERO;
                        BigDecimal qtd = item.getQuantidade() != null ? item.getQuantidade() : BigDecimal.ZERO;
                        return custoUni.multiply(qtd);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return BigDecimal.ZERO;
    }
}