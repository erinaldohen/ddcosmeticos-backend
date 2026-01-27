package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Audited
@Table(name = "tb_vendas")
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVenda;

    private LocalDateTime dataVenda;
    private BigDecimal valorTotal;
    private BigDecimal descontoTotal;

    // Totais Fiscais (Transparência + Reforma)
    private BigDecimal valorLiquido; // Total - Desconto
    private BigDecimal valorIbs;
    private BigDecimal valorCbs;
    private BigDecimal valorIs;

    private String clienteNome;
    private String clienteDocumento;

    @Enumerated(EnumType.STRING)
    private FormaDePagamento formaDePagamento;

    private Integer quantidadeParcelas;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ItemVenda> itens;

    // === CONTROLE FISCAL (NFC-e) ===
    @Enumerated(EnumType.STRING)
    private StatusFiscal statusNfce; // PENDENTE, AUTORIZADA, REJEITADA...

    private String chaveAcessoNfce;
    private String protocoloAutorizacao;
    private LocalDateTime dataAutorizacao;

    @Column(columnDefinition = "TEXT")
    private String xmlNota;

    @Column(columnDefinition = "TEXT")
    private String mensagemRejeicao; // Caso a SEFAZ rejeite

    private String motivoDoCancelamento; // Caso cancele no sistema
}