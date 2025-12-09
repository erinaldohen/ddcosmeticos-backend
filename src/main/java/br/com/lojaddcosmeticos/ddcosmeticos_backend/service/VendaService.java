// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/service/VendaService.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço responsável por toda a lógica de negócio do Módulo de Vendas.
 * Inclui validação, cálculo e persistência da venda e seus itens.
 */
@Service
public class VendaService {

    private static final int SCALE = 2; // Precisão para valores monetários (R$)
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ProdutoRepository produtoRepository; // Usamos o repo do Produto para consultar e atualizar estoque

    /**
     * Registra uma nova venda, processando itens, atualizando estoque e persistindo no banco.
     *
     * @param requestDTO O DTO contendo os dados da venda.
     * @return A entidade Venda persistida.
     * @throws RuntimeException Se o produto não for encontrado ou o estoque for insuficiente.
     */
    @Transactional // Garante que, se algo falhar, tudo será desfeito (rollback)
    public Venda registrarVenda(VendaRequestDTO requestDTO) {

        Venda novaVenda = new Venda();
        List<ItemVenda> itensVenda = new ArrayList<>();
        BigDecimal valorTotalBruto = BigDecimal.ZERO;

        // 1. Processar Itens e Atualizar Estoque
        for (var itemDTO : requestDTO.getItens()) {

            // Busca o produto pelo código de barras
            Produto produto = produtoRepository.findByCodigoBarras(itemDTO.getCodigoBarras());
            if (produto == null) {
                throw new RuntimeException("Produto não encontrado com EAN: " + itemDTO.getCodigoBarras());
            }

            // Validação de Estoque (CRÍTICO)
            if (produto.getQuantidadeEmEstoque().compareTo(itemDTO.getQuantidade()) < 0) {
                throw new RuntimeException("Estoque insuficiente para o produto: " + produto.getDescricao()
                        + ". Estoque atual: " + produto.getQuantidadeEmEstoque());
            }

            // Cálculo do item
            BigDecimal totalItemBruto = itemDTO.getPrecoUnitario().multiply(itemDTO.getQuantidade());
            BigDecimal valorTotalItem = totalItemBruto.subtract(itemDTO.getDescontoItem()).setScale(SCALE, ROUNDING_MODE);

            // Soma o valor do item ao total da venda
            valorTotalBruto = valorTotalBruto.add(totalItemBruto);

            // Cria o ItemVenda
            ItemVenda itemVenda = new ItemVenda();
            itemVenda.setVenda(novaVenda); // Associa a Venda (será persistida junto)
            itemVenda.setProduto(produto);
            itemVenda.setQuantidade(itemDTO.getQuantidade());
            itemVenda.setPrecoUnitario(itemDTO.getPrecoUnitario().setScale(SCALE, ROUNDING_MODE));
            itemVenda.setDescontoItem(itemDTO.getDescontoItem().setScale(SCALE, ROUNDING_MODE));
            itemVenda.setValorTotalItem(valorTotalItem);

            itensVenda.add(itemVenda);

            // 2. Atualizar o Estoque (Decremento)
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(itemDTO.getQuantidade()));
            produtoRepository.save(produto); // Persiste a mudança no estoque
        }

        // 3. Cálculo Final da Venda
        novaVenda.setItens(itensVenda);
        novaVenda.setValorTotal(valorTotalBruto.setScale(SCALE, ROUNDING_MODE));
        novaVenda.setDesconto(requestDTO.getDesconto().setScale(SCALE, ROUNDING_MODE));

        // Cálculo do Valor Líquido (Total Bruto - Desconto Global)
        BigDecimal valorLiquido = valorTotalBruto.subtract(requestDTO.getDesconto()).setScale(SCALE, ROUNDING_MODE);
        if (valorLiquido.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Erro de cálculo: Valor líquido da venda não pode ser negativo.");
        }
        novaVenda.setValorLiquido(valorLiquido);

        // 4. Persistência
        return vendaRepository.save(novaVenda);
    }
}