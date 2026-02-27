package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@Audited
@Table(name = "fornecedores")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Regra de ouro do JPA
@ToString(onlyExplicitlyIncluded = true) // Log limpo e seguro
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    @Column(nullable = false, length = 150)
    @ToString.Include
    private String razaoSocial;

    @Column(nullable = false, length = 150)
    private String nomeFantasia;

    @Column(nullable = false, unique = true, length = 18)
    @ToString.Include
    private String cnpj;

    @Column(length = 50)
    private String inscricaoEstadual;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(length = 100)
    private String contato;

    // --- Endereço ---
    @Column(length = 10)
    private String cep;

    @Column(length = 255)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2) // Otimizado para UF (Ex: SP, PE)
    private String uf;

    private boolean ativo = true;

    @Column(updatable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    // DBA: Relacionamentos LAZY sem risco de Loop Infinito graças ao @ToString otimizado acima
    @OneToMany(mappedBy = "fornecedor", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Produto> produtos;

    @OneToMany(mappedBy = "fornecedor", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<PedidoCompra> pedidosCompra;
}