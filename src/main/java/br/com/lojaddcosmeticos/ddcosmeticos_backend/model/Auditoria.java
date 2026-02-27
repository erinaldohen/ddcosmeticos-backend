package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEvento;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Regra de Ouro do JPA
@ToString(onlyExplicitlyIncluded = true)
public class Auditoria implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // updatable = false: Garante que o log não seja alterado acidentalmente no banco
    @Column(name = "data_hora", nullable = false, updatable = false)
    @ToString.Include
    private LocalDateTime dataHora = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 50, updatable = false)
    @ToString.Include
    private TipoEvento tipoEvento;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String mensagem;

    @Column(name = "usuario_responsavel", length = 150, updatable = false)
    @ToString.Include
    private String usuarioResponsavel;

    @Column(name = "entidade_afetada", length = 100, updatable = false)
    private String entidadeAfetada;

    @Column(name = "id_entidade_afetada", updatable = false)
    private Long idEntidadeAfetada;
}