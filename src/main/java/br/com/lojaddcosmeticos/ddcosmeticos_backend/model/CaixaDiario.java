package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_caixa_diario")
@Getter
@Setter
@NoArgsConstructor
@Audited // Segurança financeira: rastreia quem alterou os saldos
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Regra de Ouro JPA
@ToString(onlyExplicitlyIncluded = true) // Proteção contra travamentos de log
public class CaixaDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    private LocalDateTime dataAbertura;
    private LocalDateTime dataFechamento;

    @ManyToOne(fetch = FetchType.LAZY) // Otimizado para não carregar o usuário à toa
    @JoinColumn(name = "usuario_abertura_id")
    private Usuario usuarioAbertura;

    @OneToMany(mappedBy = "caixa", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<MovimentacaoCaixa> movimentacoes = new ArrayList<>();

    // --- VALORES DE CONTROLE DA GAVETA ---
    @Column(precision = 15, scale = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal saldoAtual = BigDecimal.ZERO; // Saldo físico "vivo" que muda a cada transação

    // --- DADOS DO FECHAMENTO CEGO ---
    @Column(precision = 15, scale = 2)
    private BigDecimal valorFisicoInformado; // O que o operador contou e digitou na tela

    @Column(precision = 15, scale = 2)
    private BigDecimal saldoEsperadoSistema; // O que a máquina calculou internamente

    @Column(precision = 15, scale = 2)
    private BigDecimal diferencaCaixa; // Quebra (negativo) ou Sobra (positivo)

    @Column(columnDefinition = "TEXT")
    private String justificativaDiferenca; // Motivo dado pelo operador para a quebra

    // --- ACUMULADORES DE FLUXO (VENDAS) ---
    @Column(precision = 15, scale = 2)
    private BigDecimal totalVendasDinheiro = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalVendasPix = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalVendasCredito = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalVendasDebito = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal totalVendasCartao = BigDecimal.ZERO; // Totalizador (Crédito + Débito)

    @Column(precision = 15, scale = 2)
    private BigDecimal totalEntradas = BigDecimal.ZERO; // Suprimentos

    @Column(precision = 15, scale = 2)
    private BigDecimal totalSaidas = BigDecimal.ZERO; // Sangrias

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StatusCaixa status;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    // --- EVENTOS DE CICLO DE VIDA ---

    @PrePersist
    public void prePersist() {
        if (this.dataAbertura == null) this.dataAbertura = LocalDateTime.now();
        if (this.status == null) this.status = StatusCaixa.ABERTO;
        this.garantirValoresNaoNulos();
        if (this.saldoAtual == null || this.saldoAtual.compareTo(BigDecimal.ZERO) == 0) {
            this.saldoAtual = this.saldoInicial;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.garantirValoresNaoNulos();
    }

    /**
     * Previne NullPointerException na hora da matemática de fechamento.
     */
    private void garantirValoresNaoNulos() {
        if (this.saldoInicial == null) this.saldoInicial = BigDecimal.ZERO;
        if (this.totalVendasDinheiro == null) this.totalVendasDinheiro = BigDecimal.ZERO;
        if (this.totalVendasPix == null) this.totalVendasPix = BigDecimal.ZERO;
        if (this.totalVendasCredito == null) this.totalVendasCredito = BigDecimal.ZERO;
        if (this.totalVendasDebito == null) this.totalVendasDebito = BigDecimal.ZERO;
        if (this.totalVendasCartao == null) this.totalVendasCartao = BigDecimal.ZERO;
        if (this.totalEntradas == null) this.totalEntradas = BigDecimal.ZERO;
        if (this.totalSaidas == null) this.totalSaidas = BigDecimal.ZERO;
    }

    /**
     * Método auxiliar chamado pelo VendaService para facilitar a consolidação.
     */
    public void atualizarTotalCartoes() {
        this.totalVendasCartao = this.totalVendasCredito.add(this.totalVendasDebito);
    }
}