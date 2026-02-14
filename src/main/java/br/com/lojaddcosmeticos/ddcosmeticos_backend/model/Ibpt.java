package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "tb_ibpt")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Ibpt {

    @Id
    private String codigo; // NCM (sem pontos)

    private String descricao;
    private BigDecimal nacional;  // Alíquota Federal Nacional
    private BigDecimal importado; // Alíquota Federal Importado
    private BigDecimal estadual;  // Alíquota Estadual
    private BigDecimal municipal; // Alíquota Municipal
    private String versao;
}