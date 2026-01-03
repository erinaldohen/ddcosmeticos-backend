package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO; // Import necessário
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
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
import java.util.stream.Collectors;

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
            cliente = clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RuntimeException("Cliente não encontrado para ID: " + clienteId));
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

            boolean ehAvista = formaPagamento.equals("DINHEIRO") || formaPagamento.equals("PIX") || formaPagamento.equals("DEBITO");
            if (ehAvista) {
                conta.setStatus(StatusConta.PAGO);
                conta.setDataPagamento(LocalDate.now());
                conta.setValorPago(conta.getValorTotal());
            } else {
                conta.setStatus(StatusConta.PENDENTE);
            }

            if (i == parcelas) {
                conta.setValorTotal(conta.getValorTotal().add(resto));
                conta.setValorLiquido(conta.getValorLiquido().add(resto));
                if (ehAvista) conta.setValorPago(conta.getValorTotal());
            }
            contaReceberRepository.save(conta);
        }
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

            if (i == parcelas) {
                conta.setValorTotal(conta.getValorTotal().add(resto));
            }
            contaPagarRepository.save(conta);
        }
    }

    // --- CORREÇÃO: Método renomeado, recebendo DTO e retornando Objeto ---
    @Transactional
    public MovimentacaoCaixa registrarMovimentacaoManual(MovimentacaoDTO dto, String usuarioResponsavel) {
        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.setValor(dto.getValor());
        movimentacao.setTipo(dto.getTipo()); // Certifique-se que o DTO usa o Enum TipoMovimentacaoCaixa
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
            throw new RuntimeException("Pagamento parcial não suportado ou valor insuficiente.");
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
            throw new RuntimeException("Pagamento parcial não suportado ou valor insuficiente.");
        }

        // CORREÇÃO CRÍTICA: Não sobrescreva o valorTotal (dívida) com o valorPago.
        // Use setValorPago. Se a entidade ContaPagar não tiver, adicione o campo nela.
        // Se realmente não tiver, use setValorTotal apenas se tiver certeza que quer perder o histórico da dívida original.
        // O ideal é:
        // conta.setValorPago(valorPago);
        conta.setDataPagamento(LocalDate.now());
        conta.setStatus(StatusConta.PAGO);
        contaPagarRepository.save(conta);
    }

    @Transactional(readOnly = true)
    public FechamentoCaixaDTO gerarResumoFechamento(LocalDate data) {
        List<ContaReceber> recebimentos = contaReceberRepository.findAll().stream()
                .filter(c -> data.equals(c.getDataPagamento()) && StatusConta.PAGO.equals(c.getStatus()))
                .collect(Collectors.toList());

        BigDecimal totalDinheiro = somarPorFormaPagamento(recebimentos, "DINHEIRO");
        BigDecimal totalPix = somarPorFormaPagamento(recebimentos, "PIX");
        BigDecimal totalCartao = somarPorFormaPagamento(recebimentos, "CREDITO")
                .add(somarPorFormaPagamento(recebimentos, "DEBITO"));

        BigDecimal totalEntradas = totalDinheiro.add(totalPix).add(totalCartao);

        List<ContaPagar> pagamentos = contaPagarRepository.findAll().stream()
                .filter(c -> data.equals(c.getDataPagamento()) && StatusConta.PAGO.equals(c.getStatus()))
                .collect(Collectors.toList());

        BigDecimal totalSaidas = pagamentos.stream()
                .map(ContaPagar::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> formas = new HashMap<>();
        formas.put("DINHEIRO", totalDinheiro);
        formas.put("PIX", totalPix);
        formas.put("CARTAO", totalCartao);

        return FechamentoCaixaDTO.builder()
                .data(data)
                .quantidadeVendas(recebimentos.stream().map(ContaReceber::getIdVendaRef).distinct().count())
                .totalVendasBruto(totalEntradas)
                .totalSangrias(totalSaidas)
                .totalSuprimentos(BigDecimal.ZERO)
                .totaisPorFormaPagamento(formas)
                .saldoFinalDinheiroEmEspecie(totalDinheiro.subtract(totalSaidas))
                .build();
    }

    private BigDecimal somarPorFormaPagamento(List<ContaReceber> lista, String forma) {
        return lista.stream()
                .filter(c -> forma.equalsIgnoreCase(c.getFormaPagamento()))
                .map(ContaReceber::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}