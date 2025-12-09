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
    private MovimentoEstoqueRepository movimentoEstoqueRepository; // Novo Repositório Injetado

    /**
     * Registra uma nova venda, processando itens, atualizando estoque e persistindo no banco.
     *
     * @param requestDTO O DTO contendo os dados da venda.
     * @return A entidade Venda persistida.
     * @throws RuntimeException Se o produto não for encontrado ou o estoque for insuficiente.
     */
    @Transactional
    public Venda registrarVenda(VendaRequestDTO requestDTO) {

        Venda novaVenda = new Venda();
        List<ItemVenda> itensVenda = new ArrayList<>();
        BigDecimal valorTotalBruto = BigDecimal.ZERO;

        // 1. Pré-persistência da Venda (necessário para ter o ID de Referência)
        // O JPA salvará a venda no final da transação, mas o objeto 'novaVenda' já existe.

        // 2. Processar Itens e Atualizar Estoque
        for (var itemDTO : requestDTO.getItens()) {

            Produto produto = produtoRepository.findByCodigoBarras(itemDTO.getCodigoBarras());
            if (produto == null) {
                throw new RuntimeException("Produto não encontrado com EAN: " + itemDTO.getCodigoBarras());
            }

            // Validação de Estoque (CRÍTICO)
            if (produto.getQuantidadeEmEstoque().compareTo(itemDTO.getQuantidade()) < 0) {
                throw new RuntimeException("Estoque insuficiente para o produto: " + produto.getDescricao());
            }

            // Cálculo e Criação do ItemVenda (Lógica Mantida)
            BigDecimal totalItemBruto = itemDTO.getPrecoUnitario().multiply(itemDTO.getQuantidade());
            BigDecimal valorTotalItem = totalItemBruto.subtract(itemDTO.getDescontoItem()).setScale(SCALE, ROUNDING_MODE);

            valorTotalBruto = valorTotalBruto.add(totalItemBruto);

            ItemVenda itemVenda = new ItemVenda();
            itemVenda.setVenda(novaVenda);
            itemVenda.setProduto(produto);
            itemVenda.setQuantidade(itemDTO.getQuantidade());
            itemVenda.setPrecoUnitario(itemDTO.getPrecoUnitario().setScale(SCALE, ROUNDING_MODE));
            itemVenda.setDescontoItem(itemDTO.getDescontoItem().setScale(SCALE, ROUNDING_MODE));
            itemVenda.setValorTotalItem(valorTotalItem);

            itensVenda.add(itemVenda);

            // 3. Atualizar o Estoque (Decremento e Auditoria)

            // A. Decremento de Estoque
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(itemDTO.getQuantidade()));
            produtoRepository.save(produto); // Persiste a mudança no estoque

            // B. Registro de Movimento (AUDITORIA)
            MovimentoEstoque movimento = new MovimentoEstoque();
            movimento.setProduto(produto);
            // Saída é um valor negativo
            movimento.setQuantidadeMovimentada(itemDTO.getQuantidade().negate());
            movimento.setTipoMovimento("VENDA_PDV");
            // O ID de Referência será o ID da Venda (persistido após o save da Venda)
            // É necessário salvar a Venda primeiro para ter o ID

            // Por causa do @Transactional, podemos salvar o movimento agora e setar a referência depois.
            // Para simplificar, vamos salvar a Venda primeiro, obter o ID, e depois salvar o Movimento.

            // REMOVIDO: O save do Movimento será feito após o save da Venda.
        }

        // 4. Salvar a Venda (para obter o ID)
        novaVenda.setItens(itensVenda);
        novaVenda.setValorTotal(valorTotalBruto.setScale(SCALE, ROUNDING_MODE));
        novaVenda.setDesconto(requestDTO.getDesconto().setScale(SCALE, ROUNDING_MODE));
        BigDecimal valorLiquido = valorTotalBruto.subtract(requestDTO.getDesconto()).setScale(SCALE, ROUNDING_MODE);
        novaVenda.setValorLiquido(valorLiquido);

        Venda vendaPersistida = vendaRepository.save(novaVenda);

        // 5. Auditoria Final (Usa o ID da Venda Persistida)
        for (ItemVenda item : vendaPersistida.getItens()) {
            MovimentoEstoque movimento = new MovimentoEstoque();
            movimento.setProduto(item.getProduto());
            movimento.setQuantidadeMovimentada(item.getQuantidade().negate());
            movimento.setTipoMovimento("VENDA_PDV");
            movimento.setIdReferencia(vendaPersistida.getId()); // Referência à venda

            movimentoEstoqueRepository.save(movimento);
        }

        return vendaPersistida;
    }
}