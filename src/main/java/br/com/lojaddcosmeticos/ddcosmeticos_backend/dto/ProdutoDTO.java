package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProdutoDTO {

    private Long id;
    private String codigoBarras;
    private String descricao;
    private BigDecimal precoVendaVarejo;
    private BigDecimal quantidadeEmEstoque;
    private String unidade;
    private Boolean movimentaEstoque;
    private String ncm;

    // Construtor para mapear de Entidade (Produto) para DTO
    public ProdutoDTO(Produto produto) {
        this.id = produto.getId();
        this.codigoBarras = produto.getCodigoBarras();
        this.descricao = produto.getDescricao();
        this.precoVendaVarejo = produto.getPrecoVendaVarejo();
        this.quantidadeEmEstoque = produto.getQuantidadeEmEstoque();
        // Os campos 'unidade' e 'movimentaEstoque' serão adicionados à entidade Produto
        // e populados na importação se necessário. Por enquanto, valores fictícios:
        this.unidade = "UN";
        this.movimentaEstoque = true;
        this.ncm = produto.getNcm();
    }
}