package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.PedidoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPedido;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.PedidoCompraRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

@Service
public class PedidoCompraService {

    private final PedidoCompraRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final CalculadoraFiscalService calculadoraFiscal;
    private final EstoqueService estoqueService;
    private final FinanceiroService financeiroService;
    private final FornecedorRepository fornecedorRepository;

    public PedidoCompraService(PedidoCompraRepository pedidoRepository, ProdutoRepository produtoRepository,
                               CalculadoraFiscalService calculadoraFiscal, EstoqueService estoqueService,
                               FinanceiroService financeiroService, FornecedorRepository fornecedorRepository) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.calculadoraFiscal = calculadoraFiscal;
        this.estoqueService = estoqueService;
        this.financeiroService = financeiroService;
        this.fornecedorRepository = fornecedorRepository;
    }

    @Transactional
    public PedidoCompra criarSimulacao(PedidoCompraDTO dto) {
        PedidoCompra pedido = new PedidoCompra();
        pedido.setFornecedorNome(dto.getFornecedorNome());
        pedido.setUfOrigem(dto.getUfOrigem());
        pedido.setUfDestino(dto.getUfDestino());
        pedido.setStatus(StatusPedido.EM_COTACAO);
        pedido.setItens(new ArrayList<>());

        BigDecimal totalProdutos = BigDecimal.ZERO;
        BigDecimal totalImpostos = BigDecimal.ZERO;

        for (ItemCompraDTO itemDto : dto.getItens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado no BD: " + itemDto.getCodigoBarras()));

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
            if (itemDto.getQuantidade().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("A quantidade do produto " + produto.getDescricao() + " deve ser maior que zero.");
            }

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
                .orElseThrow(() -> new ResourceNotFoundException("Pedido de Compra nº " + idPedido + " não encontrado."));

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new ValidationException("Não é possível receber um pedido que já foi cancelado.");
        }

        if (pedido.getStatus() == StatusPedido.CONCLUIDO) {
            throw new ValidationException("Este pedido já foi recebido e processado no estoque anteriormente.");
        }

        Fornecedor fornecedor = fornecedorRepository.findByRazaoSocialIgnoreCase(pedido.getFornecedorNome())
                .orElseThrow(() -> new ValidationException(
                        "Fornecedor '" + pedido.getFornecedorNome() + "' não encontrado no cadastro. " +
                                "Cadastre-o com este nome exato antes de dar entrada na nota para não gerar erros no Contas a Pagar."
                ));

        for (ItemPedidoCompra item : pedido.getItens()) {
            estoqueService.processarEntradaDePedido(
                    item.getProduto(),
                    item.getQuantidade(),
                    item.getCustoFinalUnitario(),
                    fornecedor,
                    numeroNotaFiscal
            );
        }

        financeiroService.lancarDespesaDeCompra(
                null,
                fornecedor.getId(),
                pedido.getTotalFinal(),
                1,
                "Entrada NF: " + numeroNotaFiscal + " | Pedido Interno: " + idPedido
        );

        pedido.setStatus(StatusPedido.CONCLUIDO);
        pedidoRepository.save(pedido);
    }
}