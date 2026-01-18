package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Audited
@Table(name = "fornecedores")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String razaoSocial;

    @Column(nullable = false)
    private String nomeFantasia;

    @Column(nullable = false, unique = true, length = 18)
    private String cnpj;

    private String inscricaoEstadual;

    private String email;
    private String telefone;
    private String contato;

    // Endereço
    private String cep;
    private String logradouro;
    private String numero;
    private String bairro;
    private String cidade;
    private String uf;

    private boolean ativo = true;

    @Column(updatable = false)
    private LocalDateTime dataCadastro = LocalDateTime.now();

    // --- PROTEÇÃO CONTRA LOOP INFINITO (ERRO 500) ---

    @OneToMany(mappedBy = "fornecedor", fetch = FetchType.LAZY)
    @JsonIgnore // Essencial: Ignora a lista de produtos ao buscar o fornecedor
    @ToString.Exclude // Essencial: Evita loop no Lombok
    @EqualsAndHashCode.Exclude
    private List<Produto> produtos = new ArrayList<>();

    @OneToMany(mappedBy = "fornecedor", fetch = FetchType.LAZY)
    @JsonIgnore // Essencial: Ignora a lista de pedidos de compra
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<PedidoCompra> pedidosCompra = new ArrayList<>();
}