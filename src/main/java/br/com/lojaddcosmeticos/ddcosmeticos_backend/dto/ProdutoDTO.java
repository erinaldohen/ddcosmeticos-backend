// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/ProdutoDTO.java (CORREÇÃO)

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ProdutoDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;

    private Long id;
    private String codigoBarras;
    private String descricao;
    private BigDecimal precoVendaVarejo;
    private BigDecimal quantidadeEmEstoque;
    private String unidade;
    private Boolean movimentaEstoque;
    private String ncm;

    // NOVOS CAMPOS ADICIONADOS PARA AUDITORIA E PMP
    private BigDecimal precoCustoInicial;
    private BigDecimal precoMedioPonderado;

    // Construtor para mapear de Entidade (Produto) para DTO
    public ProdutoDTO(Produto produto) {
        this.id = produto.getId();
        this.codigoBarras = produto.getCodigoBarras();
        this.descricao = produto.getDescricao();
        this.precoVendaVarejo = produto.getPrecoVenda();
        this.quantidadeEmEstoque = produto.getQuantidadeEmEstoque();

        // Novos campos
        this.precoCustoInicial = produto.getPrecoCustoInicial();
        this.precoMedioPonderado = produto.getPrecoMedioPonderado();

        // Campos auxiliares que estavam no DTO
        this.unidade = "UN";
        this.movimentaEstoque = true;
        this.ncm = produto.getNcm();
    }
}