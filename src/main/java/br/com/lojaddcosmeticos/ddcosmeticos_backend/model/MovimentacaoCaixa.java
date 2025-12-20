package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "movimentacao_caixa")
public class MovimentacaoCaixa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private TipoMovimentacaoCaixa tipo; // SANGRIA ou SUPRIMENTO

    private BigDecimal valor;
    private String motivo;
    private LocalDateTime dataHora = LocalDateTime.now();
    private String usuarioResponsavel; // Auditoria: quem tirou/colocou o dinheiro
}