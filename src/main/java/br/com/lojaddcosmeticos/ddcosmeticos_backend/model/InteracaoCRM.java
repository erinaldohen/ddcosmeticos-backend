package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_interacao_crm")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InteracaoCRM {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario vendedor;

    @Column(name = "data_contato", nullable = false)
    private LocalDateTime dataContato;

    @Column(name = "tipo_abordagem", nullable = false)
    private String tipoAbordagem; // Ex: "REPOSICAO", "CHURN", "UPSELL"

    @Column(name = "resultado", nullable = false)
    private String resultado; // Ex: "ENVIADA", "NAO_RESPONDEU", "COMPROU"

    @Column(length = 500)
    private String observacao; // Uma nota livre do vendedor, se necessário
}