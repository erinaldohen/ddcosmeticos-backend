package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore; // <--- IMPORTANTE
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "tb_item_venda")
@Audited
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venda_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore // <--- ADICIONE ISSO: Impede o loop infinito no JSON
    private Venda venda;

    @ManyToOne
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private BigDecimal quantidade;
    private BigDecimal precoUnitario;
    private BigDecimal desconto;

    // ... outros campos (custo, impostos) ...
    private BigDecimal custoUnitarioHistorico;
    private BigDecimal aliquotaIbsAplicada;
    private BigDecimal aliquotaCbsAplicada;
}