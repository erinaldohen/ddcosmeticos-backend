package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@Table(name = "regra_tributaria")
public class RegraTributaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ano_referencia")
    private Integer anoReferencia;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    // Alíquotas do Novo Regime (IBS/CBS)
    @Column(name = "aliquota_ibs", precision = 10, scale = 4)
    private BigDecimal aliquotaIbs; // Estadual

    @Column(name = "aliquota_cbs", precision = 10, scale = 4)
    private BigDecimal aliquotaCbs; // Federal

    // Fator de redução do regime antigo (1.0 = 100%, 0.9 = 90%, 0.0 = 0%)
    // Ex: Em 2029, o ICMS cai para 90% (fator 0.9)
    @Column(name = "fator_reducao_antigo", precision = 10, scale = 4)
    private BigDecimal fatorReducaoAntigo;

    // Construtor utilitário para o DataSeeder
    public RegraTributaria(Integer ano, LocalDate inicio, LocalDate fim, String ibs, String cbs, String fator) {
        this.anoReferencia = ano;
        this.dataInicio = inicio;
        this.dataFim = fim;
        this.aliquotaIbs = new BigDecimal(ibs);
        this.aliquotaCbs = new BigDecimal(cbs);
        this.fatorReducaoAntigo = new BigDecimal(fator);
    }
}