package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.FormaPagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class FinanceiroService {

    @Autowired
    private ContaPagarRepository contaPagarRepository;

    /**
     * Lança a despesa de compra.
     * Se for Parcela Única e tiver dataVencimentoManual, usa ela.
     * Se for Parcelado, ignora a manual e gera 30/60/90 dias.
     */
    public void lancarDespesaDeCompra(Fornecedor fornecedor,
                                      BigDecimal valorTotal,
                                      String numeroNota,
                                      FormaPagamento formaPagamento,
                                      Integer parcelas,
                                      LocalDate dataVencimentoManual) { // <--- NOVO PARÂMETRO

        int qtdParcelas = (parcelas != null && parcelas > 0) ? parcelas : 1;
        String nota = (numeroNota != null ? numeroNota : "S/N");

        // 1. Pagamento Imediato (PIX, Dinheiro...)
        if (ehPagamentoImediato(formaPagamento)) {
            ContaPagar conta = criarContaBase(fornecedor, valorTotal, nota, 1, 1);
            conta.setDescricao("Compra Estoque (" + formaPagamento + ") - NF: " + nota);
            conta.setDataVencimento(LocalDate.now());
            conta.setDataPagamento(LocalDate.now());
            conta.setStatus(ContaPagar.StatusConta.PAGO);
            contaPagarRepository.save(conta);
        }
        // 2. Pagamento Futuro (Boleto, Crédito...)
        else {
            BigDecimal valorParcela = valorTotal.divide(new BigDecimal(qtdParcelas), 2, RoundingMode.HALF_EVEN);
            BigDecimal somaParcelas = valorParcela.multiply(new BigDecimal(qtdParcelas));
            BigDecimal diferenca = valorTotal.subtract(somaParcelas);

            for (int i = 1; i <= qtdParcelas; i++) {
                ContaPagar conta = criarContaBase(fornecedor, valorParcela, nota, i, qtdParcelas);

                // Ajuste de centavos na última parcela
                if (i == qtdParcelas) {
                    conta.setValorTotal(valorParcela.add(diferenca));
                }

                // LÓGICA DE VENCIMENTO
                if (qtdParcelas == 1 && dataVencimentoManual != null) {
                    // Se é 1x e o usuário escolheu a data, respeita ela (Ex: Boleto para dia 15)
                    conta.setDataVencimento(dataVencimentoManual);
                } else {
                    // Se é parcelado ou não informou data, usa regra de 30 dias
                    conta.setDataVencimento(LocalDate.now().plusDays((long) i * 30));
                }

                conta.setStatus(ContaPagar.StatusConta.PENDENTE);
                contaPagarRepository.save(conta);
            }
        }
    }

    private boolean ehPagamentoImediato(FormaPagamento forma) {
        return forma == FormaPagamento.PIX ||
                forma == FormaPagamento.DINHEIRO ||
                forma == FormaPagamento.DEBITO;
    }

    private ContaPagar criarContaBase(Fornecedor f, BigDecimal valor, String nota, int parcAtual, int totalParc) {
        ContaPagar c = new ContaPagar();
        c.setFornecedor(f);
        c.setValorTotal(valor);
        c.setDataEmissao(LocalDate.now());
        if (totalParc > 1) {
            c.setDescricao(String.format("Compra Estoque - NF: %s (Parc %d/%d)", nota, parcAtual, totalParc));
        } else {
            c.setDescricao("Compra Estoque - NF: " + nota);
        }
        return c;
    }
}