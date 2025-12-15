package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.PedidoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.PedidoCompraRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

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

            BigDecimal valorTotalItem = itemDto.getPrecoUnitario().multiply(itemDto.getQuantidade());

            BigDecimal impostoItem = calculadoraFiscal.calcularImposto(
                    valorTotalItem,
                    item.getMvaAplicada(),
                    dto.getUfOrigem(),
                    dto.getUfDestino()
            );

            item.setValorIcmsSt(impostoItem);

            BigDecimal custoFinalTotal = valorTotalItem.add(impostoItem);
            BigDecimal custoUnitarioReal = custoFinalTotal.divide(itemDto.getQuantidade(), 4, RoundingMode.HALF_UP);
            item.setCustoFinalUnitario(custoUnitarioReal);

            pedido.getItens().add(item);
            totalProdutos = totalProdutos.add(valorTotalItem);
            totalImpostos = totalImpostos.add(impostoItem);
        }

        pedido.setTotalProdutos(totalProdutos);
        pedido.setTotalImpostosEstimados(totalImpostos);
        pedido.setTotalFinal(totalProdutos.add(totalImpostos));

        return pedidoRepository.save(pedido);
    }

    @Transactional
    public void receberMercadoria(Long idPedido, String numeroNotaFiscal, java.time.LocalDate dataVencimento) {

        PedidoCompra pedido = pedidoRepository.findById(idPedido)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));

        if (pedido.getStatus() == PedidoCompra.StatusPedido.CANCELADO) {
            throw new IllegalStateException("Não é possível receber um pedido cancelado.");
        }

        if (pedido.getStatus() == PedidoCompra.StatusPedido.CONCLUIDO) {
            throw new IllegalStateException("Este pedido já foi recebido anteriormente.");
        }

        Optional<Fornecedor> fornecedorOpt = fornecedorRepository.findAll().stream()
                .filter(f -> f.getRazaoSocial().equalsIgnoreCase(pedido.getFornecedorNome()))
                .findFirst();

        Fornecedor fornecedor = fornecedorOpt.orElseGet(() -> fornecedorRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Cadastre um fornecedor antes de receber o pedido.")));

        // Processa Estoque
        for (ItemPedidoCompra item : pedido.getItens()) {
            estoqueService.processarEntradaDePedido(
                    item.getProduto(),
                    item.getQuantidade(),
                    item.getCustoFinalUnitario(),
                    fornecedor,
                    numeroNotaFiscal
            );
        }

        // CORREÇÃO: Chama o Financeiro com a Nova Assinatura
        financeiroService.lancarDespesaDeCompra(
                fornecedor,
                pedido.getTotalFinal(),
                numeroNotaFiscal,
                FormaPagamento.BOLETO,
                1,
                dataVencimento // <--- PASSA A DATA QUE VEIO DO CONTROLLER
        );

        pedido.setStatus(PedidoCompra.StatusPedido.CONCLUIDO);
        pedidoRepository.save(pedido);
    }
}