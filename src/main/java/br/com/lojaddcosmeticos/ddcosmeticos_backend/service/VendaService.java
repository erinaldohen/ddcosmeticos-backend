// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/service/VendaService.java (CORREÇÃO)

package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque; // Nova Importação
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository; // Novo Repositório
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
 * Inclui validação, cálculo, persistência da venda, atualização de estoque e auditoria.
 */
@Service
public class VendaService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    @Transactional
    public Venda registrarVenda(VendaRequestDTO requestDTO) {

        Venda novaVenda = new Venda();
        List<ItemVenda> itensVenda = new ArrayList<>();
        BigDecimal valorTotalBruto = BigDecimal.ZERO;
        // NOVO: Acumulador de descontos por item
        BigDecimal totalDescontoItem = BigDecimal.ZERO;

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
            BigDecimal precoUnitario = itemDTO.getPrecoUnitario().setScale(SCALE, ROUNDING_MODE);
            BigDecimal descontoItem = itemDTO.getDescontoItem().setScale(SCALE, ROUNDING_MODE);

            BigDecimal totalItemBruto = precoUnitario.multiply(itemDTO.getQuantidade());
            BigDecimal valorTotalItem = totalItemBruto.subtract(descontoItem).setScale(SCALE, ROUNDING_MODE);

            // Acumulação
            valorTotalBruto = valorTotalBruto.add(totalItemBruto);
            totalDescontoItem = totalDescontoItem.add(descontoItem); // ACUMULA O DESCONTO DO ITEM

            // Cria o ItemVenda
            ItemVenda itemVenda = new ItemVenda();
            itemVenda.setVenda(novaVenda);
            itemVenda.setProduto(produto);
            itemVenda.setQuantidade(itemDTO.getQuantidade());
            itemVenda.setPrecoUnitario(precoUnitario);
            itemVenda.setDescontoItem(descontoItem);
            itemVenda.setValorTotalItem(valorTotalItem);

            itensVenda.add(itemVenda);

            // 2. Atualizar o Estoque (Decremento)
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(itemDTO.getQuantidade()));
            produtoRepository.save(produto);
        }

        // 3. Cálculo Final da Venda
        novaVenda.setItens(itensVenda);
        novaVenda.setValorTotal(valorTotalBruto.setScale(SCALE, ROUNDING_MODE));

        // Obtém o Desconto Global
        BigDecimal descontoGlobal = requestDTO.getDesconto().setScale(SCALE, ROUNDING_MODE);
        novaVenda.setDesconto(descontoGlobal); // Mantém no BD apenas o desconto global

        // Desconto total = Descontos por Item + Desconto Global
        BigDecimal descontoTotalVenda = totalDescontoItem.add(descontoGlobal);

        // CÁLCULO CORRETO DO VALOR LÍQUIDO: Total Bruto - Desconto Total
        BigDecimal valorLiquido = valorTotalBruto.subtract(descontoTotalVenda).setScale(SCALE, ROUNDING_MODE);

        if (valorLiquido.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Erro de cálculo: Valor líquido da venda não pode ser negativo.");
        }
        novaVenda.setValorLiquido(valorLiquido); // NOVO: R$ 20.00 - R$ 1.50 = R$ 18.50

        // 4. Persistência
        Venda vendaPersistida = vendaRepository.save(novaVenda);

        // 5. Auditoria Final (Usa o ID da Venda Persistida)
        for (ItemVenda item : vendaPersistida.getItens()) {
            MovimentoEstoque movimento = new MovimentoEstoque();
            movimento.setProduto(item.getProduto());
            movimento.setQuantidadeMovimentada(item.getQuantidade().negate());
            movimento.setTipoMovimento("VENDA_PDV");
            movimento.setIdReferencia(vendaPersistida.getId());

            movimentoEstoqueRepository.save(movimento);
        }

        return vendaPersistida;
    }

    /**
     * Busca uma venda completa pelo ID.
     * @param id O ID da venda.
     * @return A entidade Venda, se encontrada.
     */
    public Venda buscarPorId(Long id) {
        // Usa o .get() pois esperamos que a venda exista após o registro (ou lança 404 no Controller)
        return vendaRepository.findById(id).orElse(null);
    }
}