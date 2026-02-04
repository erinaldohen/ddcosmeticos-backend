package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import com.fasterxml.jackson.annotation.JsonIgnore; // Importante para evitar loop infinito no JSON
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList; // Importante
import java.util.List;      // Importante

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

    // --- RELACIONAMENTO QUE FALTAVA ---
    // Um Caixa tem MUITAS Movimentações
    @OneToMany(mappedBy = "caixa", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore // Evita erro de recursão infinita ao listar caixas (StackOverflowError)
    private List<MovimentacaoCaixa> movimentacoes = new ArrayList<>();
    // ----------------------------------

    // Valores Principais
    private BigDecimal saldoInicial;
    private BigDecimal valorFechamento;
    private BigDecimal valorCalculadoSistema;

    // Acumuladores (que adicionamos no passo anterior)
    @Column(columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal totalVendasDinheiro = BigDecimal.ZERO;

    @Column(columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal totalVendasPix = BigDecimal.ZERO;

    @Column(columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal totalVendasCartao = BigDecimal.ZERO;

    @Column(columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal totalEntradas = BigDecimal.ZERO;

    @Column(columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private BigDecimal totalSaidas = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private StatusCaixa status;

    private String observacoes;
}