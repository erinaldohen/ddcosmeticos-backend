package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited; // <--- IMPORTANTE

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vendas")
@Data
@NoArgsConstructor
@Audited // <--- ADICIONADO: Corrige o erro do EnversMappingException
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idVenda;

    private LocalDateTime dataVenda;

    @Column(name = "valor_total", precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "desconto_total", precision = 10, scale = 2)
    private BigDecimal descontoTotal;

    @Enumerated(EnumType.STRING)
    private FormaDePagamento formaDePagamento;

    @Column(name = "quantidade_parcelas")
    private Integer quantidadeParcelas = 1;

    private String clienteNome;
    private String clienteDocumento;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // mappedBy indica que quem manda na relação é o ItemVenda
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();

    // --- INTELIGÊNCIA FISCAL ---
    @Column(name = "valor_ibs", precision = 10, scale = 2)
    private BigDecimal valorIbs;

    @Column(name = "valor_cbs", precision = 10, scale = 2)
    private BigDecimal valorCbs;

    @Column(name = "valor_is", precision = 10, scale = 2)
    private BigDecimal valorIs;

    @Column(name = "valor_liquido", precision = 10, scale = 2)
    private BigDecimal valorLiquido;

    // --- CONTROLE FISCAL ---
    private String chaveAcessoNfce;

    @Enumerated(EnumType.STRING)
    private StatusFiscal statusNfce;

    private Integer numeroNfce;
    private Integer serieNfce;

    @Column(length = 500)
    private String motivoDoCancelamento;

    public void adicionarItem(ItemVenda item) {
        itens.add(item);
        item.setVenda(this);
    }
}