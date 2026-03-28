package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_insight_ia")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightIA {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipo; // VALIDADE, RUPTURA, FRAUDE
    private String criticidade; // ALTA, MEDIA, BAIXA
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Column(columnDefinition = "TEXT")
    private String acaoSugerida;

    private LocalDateTime dataGeracao;
    private boolean resolvido = false;
}