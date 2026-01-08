package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinanceiroService {

    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private MovimentacaoCaixaRepository movimentacaoCaixaRepository;

    @Transactional
    public void lancarReceitaDeVenda(Long vendaId, BigDecimal valorTotalVenda, String formaPagamento, int parcelas, Long clienteId) {
        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada"));

        Cliente cliente = null;
        if(clienteId != null) {
            cliente = clienteRepository.findById(clienteId).orElse(null);
        }

        BigDecimal valorPorParcela = valorTotalVenda.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        BigDecimal resto = valorTotalVenda.subtract(valorPorParcela.multiply(BigDecimal.valueOf(parcelas)));

        for (int i = 1; i <= parcelas; i++) {
            ContaReceber conta = new ContaReceber();
            conta.setIdVendaRef(venda.getId());
            conta.setCliente(cliente);
            conta.setValorTotal(valorPorParcela);
            conta.setValorLiquido(valorPorParcela);
            conta.setDataEmissao(LocalDate.now());
            conta.setDataVencimento(LocalDate.now().plusMonths(i-1));
            conta.setFormaPagamento(formaPagamento);
            conta.setHistorico("Venda #" + vendaId + " - Parcela " + i + "/" + parcelas + " (" + formaPagamento + ")");

            boolean ehAvista = formaPagamento.equalsIgnoreCase("DINHEIRO") ||
                    formaPagamento.equalsIgnoreCase("PIX") ||
                    formaPagamento.equalsIgnoreCase("DEBITO");

            if (ehAvista) {
                conta.setStatus(StatusConta.PAGO);
                conta.setDataPagamento(LocalDate.now());
                conta.setValorPago(conta.getValorTotal());
            } else {
                conta.setStatus(StatusConta.PENDENTE);
                conta.setValorPago(BigDecimal.ZERO);
            }

            if (i == parcelas) {
                conta.setValorTotal(conta.getValorTotal().add(resto));
                conta.setValorLiquido(conta.getValorLiquido().add(resto));
                if (ehAvista) conta.setValorPago(conta.getValorTotal());
            }
            contaReceberRepository.save(conta);
        }
    }

    // NOVO MÉTODO PARA CORRIGIR ERRO NO VENDASERVICE
    @Transactional
    public void cancelarReceitaDeVenda(Long vendaId) {
        // Busca todas as contas a receber vinculadas a esta venda e as remove/cancela
        List<ContaReceber> contas = contaReceberRepository.findByIdVendaRef(vendaId);
        contaReceberRepository.deleteAll(contas);
    }

    @Transactional
    public void lancarDespesaDeCompra(Long produtoId, Long fornecedorId, BigDecimal valorTotalCompra, int parcelas, String observacao) {
        Fornecedor fornecedor = fornecedorRepository.findById(fornecedorId)
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        BigDecimal valorPorParcela = valorTotalCompra.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        BigDecimal resto = valorTotalCompra.subtract(valorPorParcela.multiply(BigDecimal.valueOf(parcelas)));

        for (int i = 1; i <= parcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);
            conta.setValorTotal(valorPorParcela);
            conta.setDataEmissao(LocalDate.now());
            conta.setDataVencimento(LocalDate.now().plusMonths(i-1));
            conta.setStatus(StatusConta.PENDENTE);
            conta.setDescricao("Compra de Produtos - Parcela " + i + "/" + parcelas + " - " + observacao);
            conta.setValorTotal(BigDecimal.ZERO); // Cuidado: ValorTotal zero inicial? Ajuste se necessário

            if (i == parcelas) {
                conta.setValorTotal(conta.getValorTotal().add(resto));
            }
            contaPagarRepository.save(conta);
        }
    }

    @Transactional
    public MovimentacaoCaixa registrarMovimentacaoManual(MovimentacaoDTO dto, String usuarioResponsavel) {
        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.setValor(dto.getValor());
        movimentacao.setTipo(dto.getTipo());
        movimentacao.setDataHora(LocalDateTime.now());
        movimentacao.setMotivo(dto.getMotivo());
        movimentacao.setUsuarioResponsavel(usuarioResponsavel);
        return movimentacaoCaixaRepository.save(movimentacao);
    }

    @Transactional
    public void darBaixaContaReceber(Long contaReceberId, BigDecimal valorPago) {
        ContaReceber conta = contaReceberRepository.findById(contaReceberId)
                .orElseThrow(() -> new RuntimeException("Conta a receber não encontrada."));

        if (StatusConta.PAGO.equals(conta.getStatus())) {
            throw new RuntimeException("Esta conta já foi paga.");
        }
        if (valorPago.compareTo(conta.getValorTotal()) < 0) {
            throw new RuntimeException("Valor insuficiente para quitação integral.");
        }
        conta.setValorPago(valorPago);
        conta.setDataPagamento(LocalDate.now());
        conta.setStatus(StatusConta.PAGO);
        contaReceberRepository.save(conta);
    }

    @Transactional
    public void darBaixaContaPagar(Long contaPagarId, BigDecimal valorPago) {
        ContaPagar conta = contaPagarRepository.findById(contaPagarId)
                .orElseThrow(() -> new RuntimeException("Conta a pagar não encontrada."));

        if (StatusConta.PAGO.equals(conta.getStatus())) {
            throw new RuntimeException("Esta conta já foi paga.");
        }
        if (valorPago.compareTo(conta.getValorTotal()) < 0) {
            throw new RuntimeException("Valor insuficiente para quitação integral.");
        }
        conta.setValorTotal(valorPago); // Se a lógica for atualizar o valor pago, cuidado para não sobrescrever o total
        conta.setDataPagamento(LocalDate.now());
        conta.setStatus(StatusConta.PAGO);
        contaPagarRepository.save(conta);
    }

    @Transactional(readOnly = true)
    public FechamentoCaixaDTO gerarResumoFechamento(LocalDate data) {
        List<ContaReceber> recebimentos = contaReceberRepository.findByDataPagamentoAndStatus(data, StatusConta.PAGO);
        List<ContaPagar> pagamentos = contaPagarRepository.findByDataPagamentoAndStatus(data, StatusConta.PAGO);

        BigDecimal totalDinheiro = somarPorFormaPagamento(recebimentos, "DINHEIRO");
        BigDecimal totalPix = somarPorFormaPagamento(recebimentos, "PIX");
        BigDecimal totalCartao = somarPorFormaPagamento(recebimentos, "CREDITO")
                .add(somarPorFormaPagamento(recebimentos, "DEBITO"))
                .add(somarPorFormaPagamento(recebimentos, "CREDIARIO"));

        BigDecimal totalEntradas = totalDinheiro.add(totalPix).add(totalCartao);
        BigDecimal totalSaidas = pagamentos.stream()
                .map(p -> p.getValorTotal() != null ? p.getValorTotal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> formas = new HashMap<>();
        formas.put("DINHEIRO", totalDinheiro);
        formas.put("PIX", totalPix);
        formas.put("CARTAO", totalCartao);

        BigDecimal saldoDinheiro = totalDinheiro.subtract(totalSaidas);

        return FechamentoCaixaDTO.builder()
                .data(data)
                .quantidadeVendas(recebimentos.stream().map(ContaReceber::getIdVendaRef).distinct().count())
                .totalVendasBruto(totalEntradas)
                .totalSangrias(totalSaidas)
                .totalSuprimentos(BigDecimal.ZERO)
                .totaisPorFormaPagamento(formas)
                .saldoFinalDinheiroEmEspecie(saldoDinheiro)
                .build();
    }

    private BigDecimal somarPorFormaPagamento(List<ContaReceber> lista, String forma) {
        return lista.stream()
                .filter(c -> c.getFormaPagamento() != null && c.getFormaPagamento().equalsIgnoreCase(forma))
                .map(c -> c.getValorPago() != null ? c.getValorPago() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}