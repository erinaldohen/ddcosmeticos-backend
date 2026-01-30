package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString; // <--- Importante
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Audited
@Table(name = "tb_venda")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVenda;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime dataVenda;
    private BigDecimal valorTotal;
    private BigDecimal descontoTotal;

    @Column(length = 100)
    private String clienteNome;

    @Column(length = 20)
    private String clienteDocumento;

    @Enumerated(EnumType.STRING)
    private FormaDePagamento formaDePagamento; // Principal (Legado)

    private Integer quantidadeParcelas;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @ToString.Exclude // <--- EVITA O LOOP INFINITO NO LOG
    private List<ItemVenda> itens;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @ToString.Exclude
    private List<PagamentoVenda> pagamentos = new ArrayList<>();

    // Campos Fiscais
    private BigDecimal valorIbs;
    private BigDecimal valorCbs;
    private BigDecimal valorIs;
    private BigDecimal valorLiquido;

    @Enumerated(EnumType.STRING)
    private StatusFiscal statusNfce;

    @Column(length = 50)
    private String chaveAcessoNfce;

    @Column(length = 20)
    private String protocoloAutorizacao;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String xmlNota;

    @Column(columnDefinition = "TEXT")
    private String mensagemRejeicao;

    private LocalDateTime dataAutorizacao;
    private String motivoDoCancelamento;
    private String observacao;
}