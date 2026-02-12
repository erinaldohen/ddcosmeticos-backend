package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEvento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auditoria")
public class Auditoria implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    // --- CORREÇÃO AQUI ---
    // @Enumerated(EnumType.STRING) garante que grave "LOGIN" e não o número 0.
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50) // Adicionei length por segurança
    private TipoEvento tipoEvento;

    @Column(columnDefinition = "TEXT")
    private String mensagem;

    @Column(name = "usuario_responsavel")
    private String usuarioResponsavel;

    @Column(name = "entidade_afetada")
    private String entidadeAfetada;

    @Column(name = "id_entidade_afetada")
    private Long idEntidadeAfetada;
}