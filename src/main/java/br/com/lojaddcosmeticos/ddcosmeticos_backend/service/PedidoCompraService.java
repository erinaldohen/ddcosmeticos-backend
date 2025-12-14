package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.PedidoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemPedidoCompra;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PedidoCompra;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.PedidoCompraRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PedidoCompraService {

    @Autowired private PedidoCompraRepository pedidoRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CalculadoraFiscalService calculadoraFiscal;
    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private FornecedorRepository fornecedorRepository;

    @Transactional
    public PedidoCompra criarSimulacao(PedidoCompraDTO dto) {
        PedidoCompra pedido = new PedidoCompra();
        pedido.setFornecedorNome(dto.getFornecedorNome());
        pedido.setUfOrigem(dto.getUfOrigem());
        pedido.setUfDestino(dto.getUfDestino());
        pedido.setStatus(PedidoCompra.StatusPedido.EM_COTACAO);

        BigDecimal totalProdutos = BigDecimal.ZERO;
        BigDecimal totalImpostos = BigDecimal.ZERO;

        for (ItemCompraDTO itemDto : dto.getItens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.getCodigoBarras()));

            ItemPedidoCompra item = new ItemPedidoCompra();
            item.setPedidoCompra(pedido);
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitarioTabela(itemDto.getPrecoUnitario());
            item.setMvaAplicada(itemDto.getMva() != null ? itemDto.getMva() : BigDecimal.ZERO);

            // --- INTELIGÊNCIA TRIBUTÁRIA APLICADA ---
            BigDecimal valorTotalItem = itemDto.getPrecoUnitario().multiply(itemDto.getQuantidade());

            // Calcula o imposto baseado nos estados
            BigDecimal impostoItem = calculadoraFiscal.calcularImposto(
                    valorTotalItem,
                    item.getMvaAplicada(),
                    dto.getUfOrigem(),
                    dto.getUfDestino()
            );

            item.setValorIcmsSt(impostoItem);

            // Custo Final Unitário = (Preço Total + Imposto) / Quantidade
            BigDecimal custoFinalTotal = valorTotalItem.add(impostoItem);
            BigDecimal custoUnitarioReal = custoFinalTotal.divide(itemDto.getQuantidade(), 4, java.math.RoundingMode.HALF_UP);
            item.setCustoFinalUnitario(custoUnitarioReal);

            // Adiciona na lista e soma totais
            pedido.getItens().add(item);
            totalProdutos = totalProdutos.add(valorTotalItem);
            totalImpostos = totalImpostos.add(impostoItem);
        }

        pedido.setTotalProdutos(totalProdutos);
        pedido.setTotalImpostosEstimados(totalImpostos);
        pedido.setTotalFinal(totalProdutos.add(totalImpostos));

        return pedidoRepository.save(pedido);
    }

    /**
     * O BOTÃO MÁGICO: Transforma Pedido em Estoque + Dívida.
     */
    @Transactional
    public void receberMercadoria(Long idPedido, String numeroNotaFiscal, java.time.LocalDate dataVencimento) {

        PedidoCompra pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));

        if (pedido.getStatus() == PedidoCompra.StatusPedido.CANCELADO) {
            throw new IllegalStateException("Não é possível receber um pedido cancelado.");
        }

        // Evita duplicidade
        if (pedido.getStatus() == PedidoCompra.StatusPedido.CONCLUIDO) {
            throw new IllegalStateException("Este pedido já foi recebido anteriormente.");
        }

        // 1. Identifica/Cria Fornecedor (baseado no nome do pedido, simplificado)
        // Em produção, o Pedido deveria ter um vínculo direto com o ID do Fornecedor.
        // Aqui vamos buscar pelo nome ou criar um placeholder para o exemplo funcionar.
        Fornecedor fornecedor = fornecedorRepository.findAll().stream()
                .filter(f -> f.getRazaoSocial().equalsIgnoreCase(pedido.getFornecedorNome()))
                .findFirst()
                .orElseGet(() -> {
                    // Se não achar, usa um genérico (Ideal é vincular ID no DTO de criação)
                    return fornecedorRepository.findAll().stream().findFirst()
                            .orElseThrow(() -> new IllegalStateException("Nenhum fornecedor cadastrado no sistema."));
                });

        // 2. Processa cada item (Entrada no Estoque)
        for (ItemPedidoCompra item : pedido.getItens()) {
            // O custo final unitário já foi calculado na simulação (Preço + ST)
            estoqueService.processarEntradaDePedido(
                    item.getProduto(),
                    item.getQuantidade(),
                    item.getCustoFinalUnitario(), // Já com imposto rateado
                    fornecedor,
                    numeroNotaFiscal
            );
        }

        // 3. Gera Financeiro Consolidado (Valor Total do Pedido)
        financeiroService.lancarDespesaDeCompra(
                fornecedor,
                pedido.getTotalFinal(), // Valor com impostos
                numeroNotaFiscal,
                dataVencimento
        );

        // 4. Finaliza o Pedido
        pedido.setStatus(PedidoCompra.StatusPedido.CONCLUIDO);
        pedidoRepository.save(pedido);
    }
}