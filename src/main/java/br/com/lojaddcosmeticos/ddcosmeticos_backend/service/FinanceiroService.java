package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento; // Import Necessário
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor; // Import Necessário
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentacaoCaixaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinanceiroService {

    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private MovimentacaoCaixaRepository movimentacaoCaixaRepository;

    // --- NOVO MÉTODO (Resolve o erro do PedidoCompraService) ---
    @Transactional
    public void lancarDespesaDeCompra(Fornecedor fornecedor, BigDecimal total, String numeroNotaFiscal,
                                      FormaDePagamento formaPagamento, Integer parcelas, LocalDate dataVencimentoInicial) {

        int qtdParcelas = (parcelas != null && parcelas > 0) ? parcelas : 1;
        BigDecimal valorParcela = total.divide(BigDecimal.valueOf(qtdParcelas), 2, RoundingMode.DOWN);
        BigDecimal resto = total.subtract(valorParcela.multiply(BigDecimal.valueOf(qtdParcelas)));

        // Se a data de vencimento não for informada, assume 30 dias
        LocalDate dataVenc = dataVencimentoInicial != null ? dataVencimentoInicial : LocalDate.now().plusDays(30);

        for (int i = 1; i <= qtdParcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);

            // Na última parcela, soma os centavos restantes
            if (i == qtdParcelas) {
                conta.setValorTotal(valorParcela.add(resto));
            } else {
                conta.setValorTotal(valorParcela);
            }

            conta.setDataEmissao(LocalDate.now());
            conta.setStatus(StatusConta.PENDENTE);
            conta.setDescricao("Compra NF " + numeroNotaFiscal + " - Parc " + i + "/" + qtdParcelas);

            // Define vencimento (ex: 30/60/90 dias ou data fixa)
            if (i == 1) {
                conta.setDataVencimento(dataVenc);
            } else {
                conta.setDataVencimento(dataVenc.plusDays(30L * (i - 1)));
            }

            contaPagarRepository.save(conta);
        }
    }

    // --- MÉTODOS EXISTENTES ---

    @Transactional
    public MovimentacaoCaixa registrarMovimentacaoManual(MovimentacaoDTO dto, String usuarioResponsavel) {
        MovimentacaoCaixa movimentacao = new MovimentacaoCaixa();
        movimentacao.setTipo(dto.tipo());
        movimentacao.setValor(dto.valor());
        movimentacao.setMotivo(dto.motivo());
        movimentacao.setUsuarioResponsavel(usuarioResponsavel);
        return movimentacaoCaixaRepository.save(movimentacao);
    }

    @Transactional(readOnly = true)
    public FechamentoCaixaDTO gerarResumoFechamento(LocalDate data) {
        // 1. Entradas (Recebimentos do dia)
        List<ContaReceber> recebimentos = contaReceberRepository.findAll().stream()
                .filter(c -> data.equals(c.getDataVencimento()) && StatusConta.PAGO.equals(c.getStatus()))
                .toList();

        BigDecimal totalDinheiro = somarPorTipo(recebimentos, "DINHEIRO");
        BigDecimal totalPix = somarPorTipo(recebimentos, "PIX");
        BigDecimal totalCartao = somarPorTipo(recebimentos, "CREDITO").add(somarPorTipo(recebimentos, "DEBITO"));

        BigDecimal totalVendasBruto = totalDinheiro.add(totalPix).add(totalCartao);

        Map<String, BigDecimal> mapaPagamentos = new HashMap<>();
        mapaPagamentos.put("DINHEIRO", totalDinheiro);
        mapaPagamentos.put("PIX", totalPix);
        mapaPagamentos.put("CARTAO", totalCartao);

        // 2. Saídas (Contas Pagas)
        List<ContaPagar> pagamentos = contaPagarRepository.findAll().stream()
                .filter(c -> data.equals(c.getDataVencimento()) && StatusConta.PAGO.equals(c.getStatus()))
                .toList();

        BigDecimal totalSaidas = pagamentos.stream()
                .map(ContaPagar::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long qtdVendas = recebimentos.stream()
                .map(ContaReceber::getIdVendaRef)
                .distinct()
                .count();

        BigDecimal saldoFinal = totalVendasBruto.subtract(totalSaidas);

        return FechamentoCaixaDTO.builder()
                .data(data)
                .quantidadeVendas(qtdVendas)
                .totalVendasBruto(totalVendasBruto)
                .totalSuprimentos(BigDecimal.ZERO)
                .totalSangrias(totalSaidas)
                .totaisPorFormaPagamento(mapaPagamentos)
                .saldoFinalDinheiroEmEspecie(saldoFinal)
                .build();
    }

    private BigDecimal somarPorTipo(List<ContaReceber> lista, String tipo) {
        return lista.stream()
                .filter(c -> c.getFormaPagamento() != null &&
                        tipo.equalsIgnoreCase(c.getFormaPagamento().toString()))
                .map(ContaReceber::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void lancarReceitaDeVenda(Long idVenda, BigDecimal total, String formaPagamento, Integer qtdParcelasInput) {
        int parcelas = (qtdParcelasInput != null && qtdParcelasInput > 0) ? qtdParcelasInput : 1;
        BigDecimal valorParcela = total.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
        BigDecimal totalParcelado = valorParcela.multiply(BigDecimal.valueOf(parcelas));
        BigDecimal resto = total.subtract(totalParcelado);
        LocalDate dataBase = LocalDate.now();

        for (int i = 1; i <= parcelas; i++) {
            ContaReceber conta = new ContaReceber();
            conta.setIdVendaRef(idVenda);
            conta.setDataEmissao(dataBase);
            conta.setFormaPagamento(formaPagamento);
            conta.setStatus(StatusConta.PENDENTE);

            if (formaPagamento.equals("DINHEIRO") || formaPagamento.equals("PIX") || formaPagamento.equals("DEBITO")) {
                conta.setDataVencimento(dataBase);
                conta.setStatus(StatusConta.PAGO);
            } else {
                conta.setDataVencimento(dataBase.plusDays(30L * i));
            }

            if (i == parcelas) {
                conta.setValorTotal(valorParcela.add(resto));
                conta.setValorLiquido(valorParcela.add(resto));
            } else {
                conta.setValorTotal(valorParcela);
                conta.setValorLiquido(valorParcela);
            }
            contaReceberRepository.save(conta);
        }
    }
}