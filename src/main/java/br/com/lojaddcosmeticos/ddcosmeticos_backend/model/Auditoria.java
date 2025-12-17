package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data // Gera Getters, Setters, ToString, Equals, HashCode
@NoArgsConstructor // Gera construtor vazio (obrigatório pro JPA)
@AllArgsConstructor // Gera construtor com todos os campos
@Entity
@Table(name = "auditoria")
public class Auditoria implements Serializable {
    private static final long serialVersionUID = 1L;

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

    @Column(name = "entidade_afetada")
    private String entidadeAfetada;

    @Column(name = "id_entidade_afetada")
    private Long idEntidadeAfetada;
}