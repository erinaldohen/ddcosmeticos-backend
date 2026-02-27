package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "produto_fornecedor", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fornecedor_id", "codigoNoFornecedor"})
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Proteção do JPA
@ToString(onlyExplicitlyIncluded = true) // Loga sem disparar N+1 queries
public class ProdutoFornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // DBA: Alterado para LAZY. Nunca deixe @ManyToOne como EAGER (que é o padrão oculto).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id", nullable = false)
    private Fornecedor fornecedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, length = 100)
    @ToString.Include
    private String codigoNoFornecedor;
}