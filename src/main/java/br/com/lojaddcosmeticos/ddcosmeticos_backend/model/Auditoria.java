package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "auditoria")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "usuario_responsavel")
    private String usuarioResponsavel;

    // CORREÇÃO: Mapeamento explícito para o banco (snake_case)
    @Column(name = "entidade_afetada")
    private String entidadeAfetada;

    @Column(name = "id_entidade_afetada")
    private Long idEntidadeAfetada;
}