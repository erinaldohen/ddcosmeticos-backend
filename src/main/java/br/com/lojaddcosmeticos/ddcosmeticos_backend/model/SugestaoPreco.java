package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// DBA/Performance: Removido @Data para evitar N+1 queries e Loops Infinitos no toString/hashCode
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sugestao_preco")
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Regra de Ouro do JPA
@ToString(onlyExplicitlyIncluded = true) // Loga apenas dados primários
public class SugestaoPreco implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // Relacionamentos para outras tabelas nunca devem entrar no toString ou equals padrão
    @ManyToOne(fetch = FetchType.LAZY) // Otimizado: Só carrega o Produto se chamar getProduto()
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ToString.Include
    private BigDecimal custoBase;

    @ToString.Include
    private BigDecimal precoVendaAtual;

    @ToString.Include
    private BigDecimal precoVendaSugerido;

    private BigDecimal margemAtualPercentual;
    private BigDecimal margemSugeridaPercentual;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_precificacao", length = 50) // Limite seguro para enum no Postgres
    @ToString.Include
    private StatusPrecificacao statusPrecificacao;

    private LocalDateTime dataSugestao = LocalDateTime.now();
    private LocalDateTime dataAprovacao;

    // Correto para PostgreSQL
    @Column(columnDefinition = "TEXT")
    private String observacao;
}