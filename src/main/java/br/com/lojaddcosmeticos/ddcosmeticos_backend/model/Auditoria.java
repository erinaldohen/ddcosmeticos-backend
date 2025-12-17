package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

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

    // CORREÇÃO: Mapeamento explícito para o banco (snake_case)
    @Column(name = "entidade_afetada")
    private String entidadeAfetada;

    @Column(name = "id_entidade_afetada")
    private Long idEntidadeAfetada;

    public Auditoria(LocalDateTime dataHora, String tipoEvento, String mensagem, String usuarioResponsavel, String entidadeAfetada, Long idEntidadeAfetada) {
        this.dataHora = dataHora;
        this.tipoEvento = tipoEvento;
        this.mensagem = mensagem;
        this.usuarioResponsavel = usuarioResponsavel;
        this.entidadeAfetada = entidadeAfetada;
        this.idEntidadeAfetada = idEntidadeAfetada;
    }

    public Auditoria() {
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getUsuarioResponsavel() {
        return usuarioResponsavel;
    }

    public void setUsuarioResponsavel(String usuarioResponsavel) {
        this.usuarioResponsavel = usuarioResponsavel;
    }

    public String getEntidadeAfetada() {
        return entidadeAfetada;
    }

    public void setEntidadeAfetada(String entidadeAfetada) {
        this.entidadeAfetada = entidadeAfetada;
    }

    public Long getIdEntidadeAfetada() {
        return idEntidadeAfetada;
    }

    public void setIdEntidadeAfetada(Long idEntidadeAfetada) {
        this.idEntidadeAfetada = idEntidadeAfetada;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Auditoria auditoria = (Auditoria) o;
        return Objects.equals(id, auditoria.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Auditoria{" +
                "id=" + id +
                ", dataHora=" + dataHora +
                ", tipoEvento='" + tipoEvento + '\'' +
                ", mensagem='" + mensagem + '\'' +
                ", usuarioResponsavel='" + usuarioResponsavel + '\'' +
                ", entidadeAfetada='" + entidadeAfetada + '\'' +
                ", idEntidadeAfetada=" + idEntidadeAfetada +
                '}';
    }
}