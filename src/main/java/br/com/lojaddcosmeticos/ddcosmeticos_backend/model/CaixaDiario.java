package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "tb_caixa_diario")
public class CaixaDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime dataAbertura;
    private LocalDateTime dataFechamento;

    @ManyToOne
    @JoinColumn(name = "usuario_abertura_id")
    private Usuario usuarioAbertura;

    @OneToMany(mappedBy = "caixa", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<MovimentacaoCaixa> movimentacoes = new ArrayList<>();

    // --- VALORES DE CONTROLE ---
    @Column(precision = 10, scale = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorFechamento; // Valor informado pelo operador (físico)

    @Column(precision = 10, scale = 2)
    private BigDecimal valorCalculadoSistema; // O que o sistema acha que tem

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoAtual = BigDecimal.ZERO; // Saldo vivo (muda a cada venda)

    // --- ACUMULADORES (Correção: Adicionados Crédito e Débito Separados) ---

    @Column(precision = 10, scale = 2)
    private BigDecimal totalVendasDinheiro = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalVendasPix = BigDecimal.ZERO;

    // Adicionado para suportar o VendaService
    @Column(precision = 10, scale = 2)
    private BigDecimal totalVendasCredito = BigDecimal.ZERO;

    // Adicionado para suportar o VendaService
    @Column(precision = 10, scale = 2)
    private BigDecimal totalVendasDebito = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalVendasCartao = BigDecimal.ZERO; // Soma de Crédito + Débito (Totalizador)

    @Column(precision = 10, scale = 2)
    private BigDecimal totalEntradas = BigDecimal.ZERO; // Suprimentos

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSaidas = BigDecimal.ZERO; // Sangrias

    @Enumerated(EnumType.STRING)
    private StatusCaixa status;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    // Método auxiliar para Relatórios (Quebra de Caixa)
    public BigDecimal getDiferenca() {
        if (valorFechamento == null || valorCalculadoSistema == null) return BigDecimal.ZERO;
        return valorFechamento.subtract(valorCalculadoSistema);
    }

    @PrePersist
    public void prePersist() {
        if (saldoAtual == null) saldoAtual = saldoInicial != null ? saldoInicial : BigDecimal.ZERO;

        if (totalVendasDinheiro == null) totalVendasDinheiro = BigDecimal.ZERO;
        if (totalVendasPix == null) totalVendasPix = BigDecimal.ZERO;

        // Inicialização dos novos campos
        if (totalVendasCredito == null) totalVendasCredito = BigDecimal.ZERO;
        if (totalVendasDebito == null) totalVendasDebito = BigDecimal.ZERO;
        if (totalVendasCartao == null) totalVendasCartao = BigDecimal.ZERO;

        if (totalEntradas == null) totalEntradas = BigDecimal.ZERO;
        if (totalSaidas == null) totalSaidas = BigDecimal.ZERO;
    }
}