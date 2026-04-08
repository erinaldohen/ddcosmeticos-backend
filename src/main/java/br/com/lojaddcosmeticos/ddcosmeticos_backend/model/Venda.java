package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.CanalOrigem;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
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
@DynamicInsert // Otimização de Performance: Insere apenas os campos preenchidos
@DynamicUpdate // Otimização de Performance: Atualiza apenas as colunas modificadas
@Table(name = "tb_venda", indexes = {
        @Index(name = "idx_venda_data", columnList = "dataVenda"),
        @Index(name = "idx_venda_status", columnList = "statusNfce"),
        @Index(name = "idx_venda_cliente", columnList = "id_cliente")
})
// 🚨 A anotação @EqualsAndHashCode do Lombok foi removida intencionalmente!
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    // A coluna que armazenará o custo da mercadoria vendida (CMV)
    @Column(precision = 15, scale = 2)
    private BigDecimal custoTotal = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal descontoTotal = BigDecimal.ZERO;

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
    // DADOS FISCAIS NFC-E / NF-E
    // =========================================================================

    @Column(length = 50)
    private String chaveAcessoNfce;

    @Column(length = 50)
    private String protocolo;

    @Column
    private Long numeroNfce;

    @Column
    private Integer serieNfce;

    @Column(columnDefinition = "TEXT")
    private String xmlNota;

    @Column(columnDefinition = "TEXT")
    private String mensagemRejeicao;

    private LocalDateTime dataAutorizacao;

    @Column(length = 255)
    private String motivoDoCancelamento;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Column(columnDefinition = "TEXT")
    private String urlQrCode;

    // =========================================================================
    // GATILHOS DE CICLO DE VIDA JPA (INTEGRIDADE PARA RELATÓRIOS)
    // =========================================================================

    @PrePersist
    @PreUpdate
    public void consolidarTotais() {
        if (this.itens != null && !this.itens.isEmpty()) {
            this.custoTotal = this.itens.stream()
                    .map(item -> {
                        BigDecimal custoUni = item.getCustoUnitarioHistorico() != null ? item.getCustoUnitarioHistorico() : BigDecimal.ZERO;
                        BigDecimal qtd = item.getQuantidade() != null ? item.getQuantidade() : BigDecimal.ZERO;
                        return custoUni.multiply(qtd);
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            this.custoTotal = BigDecimal.ZERO;
        }
    }

    // =========================================================================
    // EQUALS E HASHCODE À PROVA DE FALHAS (JPA BEST PRACTICES)
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Venda)) return false;
        Venda venda = (Venda) o;
        return idVenda != null && idVenda.equals(venda.getIdVenda());
    }

    @Override
    public int hashCode() {
        // Retorna um valor fixo (o hash da classe) para que o hash code
        // não mude após o Hibernate gerar o ID (Evita o erro "Row was updated or deleted")
        return getClass().hashCode();
    }

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