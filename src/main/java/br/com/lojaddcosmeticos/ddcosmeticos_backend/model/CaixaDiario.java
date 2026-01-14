package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "caixa_diario")
public class CaixaDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_abertura_id", nullable = false)
    private Usuario usuarioAbertura;

    @ManyToOne
    @JoinColumn(name = "usuario_fechamento_id")
    private Usuario usuarioFechamento;

    private LocalDateTime dataAbertura;
    private LocalDateTime dataFechamento;

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoInicial; // Fundo de troco

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoFinalInformado; // O que o operador contou na gaveta

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoFinalCalculado; // O que o sistema calculou

    @Column(precision = 10, scale = 2)
    private BigDecimal diferenca; // Quebra de caixa (Sobra ou Falta)

    @Enumerated(EnumType.STRING)
    private StatusCaixa status; // ABERTO, FECHADO

    @OneToMany(mappedBy = "caixa", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MovimentacaoCaixa> movimentacoes;

    // Totais calculados para agilizar relatórios
    private BigDecimal totalVendasDinheiro = BigDecimal.ZERO;
    private BigDecimal totalVendasPix = BigDecimal.ZERO;
    private BigDecimal totalVendasCartao = BigDecimal.ZERO;
    private BigDecimal totalSangrias = BigDecimal.ZERO;
    private BigDecimal totalSuprimentos = BigDecimal.ZERO;
}