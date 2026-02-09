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

    // --- VALORES ---
    @Column(precision = 10, scale = 2)
    private BigDecimal saldoInicial = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorFechamento;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorCalculadoSistema;

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoAtual = BigDecimal.ZERO;

    // --- ACUMULADORES (CORRIGIDO: Removido columnDefinition que quebrava o H2) ---

    @Column(precision = 10, scale = 2)
    private BigDecimal totalVendasDinheiro = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalVendasPix = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalVendasCartao = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalEntradas = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSaidas = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private StatusCaixa status;

    private String observacoes;

    @PrePersist
    public void prePersist() {
        if (saldoAtual == null) saldoAtual = saldoInicial != null ? saldoInicial : BigDecimal.ZERO;
        if (totalVendasDinheiro == null) totalVendasDinheiro = BigDecimal.ZERO;
        if (totalVendasPix == null) totalVendasPix = BigDecimal.ZERO;
        if (totalVendasCartao == null) totalVendasCartao = BigDecimal.ZERO;
        if (totalEntradas == null) totalEntradas = BigDecimal.ZERO;
        if (totalSaidas == null) totalSaidas = BigDecimal.ZERO;
    }
}