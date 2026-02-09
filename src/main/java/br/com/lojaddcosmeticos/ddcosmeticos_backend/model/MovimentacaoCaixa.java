package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movimentacao_caixa")
public class MovimentacaoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id", nullable = false)
    @JsonIgnore
    private CaixaDiario caixa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentacaoCaixa tipo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    // Adicionado para suportar integração financeira (Saber se entrou em Dinheiro ou Pix)
    @Enumerated(EnumType.STRING)
    private FormaDePagamento formaPagamento;

    @Column(nullable = false)
    @JsonProperty("observacao")
    private String motivo; // O Service deve usar .setMotivo() e não .setDescricao()

    @Column(nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    private String usuarioResponsavel;
}