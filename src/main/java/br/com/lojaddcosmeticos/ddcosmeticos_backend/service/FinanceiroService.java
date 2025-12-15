package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
public class FinanceiroService {

    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;

    // ... (Método lancarDespesaDeCompra continua igual ao anterior) ...
    public void lancarDespesaDeCompra(Fornecedor fornecedor, BigDecimal valorTotal, String numeroNota, FormaPagamento formaPagamento, Integer parcelas, LocalDate dataVencimentoManual) {
        // ... (Mantenha o código que fizemos no passo anterior aqui) ...
        // Vou omitir para focar no novo método de receita, mas não apague o seu!
        int qtdParcelas = (parcelas != null && parcelas > 0) ? parcelas : 1;
        String nota = (numeroNota != null ? numeroNota : "S/N");

        if (formaPagamento == FormaPagamento.PIX || formaPagamento == FormaPagamento.DINHEIRO) { // Tirei Débito daqui
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);
            conta.setValorTotal(valorTotal);
            conta.setDataEmissao(LocalDate.now());
            conta.setDataVencimento(LocalDate.now());
            conta.setDataPagamento(LocalDate.now());
            conta.setStatus(ContaPagar.StatusConta.PAGO);
            conta.setDescricao("Compra Estoque (" + formaPagamento + ") - NF: " + nota);
            contaPagarRepository.save(conta);
        } else {
            // Lógica parcelada igual a antes
            BigDecimal valorParcela = valorTotal.divide(new BigDecimal(qtdParcelas), 2, RoundingMode.HALF_EVEN);
            BigDecimal diferenca = valorTotal.subtract(valorParcela.multiply(new BigDecimal(qtdParcelas)));
            for (int i = 1; i <= qtdParcelas; i++) {
                ContaPagar conta = new ContaPagar();
                conta.setFornecedor(fornecedor);
                conta.setDescricao(String.format("Compra Estoque - NF: %s (Parc %d/%d)", nota, i, qtdParcelas));
                if (i == qtdParcelas) conta.setValorTotal(valorParcela.add(diferenca));
                else conta.setValorTotal(valorParcela);

                conta.setDataEmissao(LocalDate.now());
                if (qtdParcelas == 1 && dataVencimentoManual != null) conta.setDataVencimento(dataVencimentoManual);
                else conta.setDataVencimento(LocalDate.now().plusDays((long) i * 30));

                conta.setStatus(ContaPagar.StatusConta.PENDENTE);
                contaPagarRepository.save(conta);
            }
        }
    }

    /**
     * Lança a RECEITA vinda do PDV.
     * Regra: Crédito e Débito caem no próximo dia útil.
     */
    public void lancarReceitaDeVenda(Long idVenda, BigDecimal valorTotal, FormaPagamento formaPagamento, Integer parcelas) {
        int qtdParcelas = (parcelas != null && parcelas > 0) ? parcelas : 1;
        String desc = "Venda PDV #" + idVenda;

        // 1. Recebimento Instantâneo (Cai na hora)
        if (formaPagamento == FormaPagamento.PIX || formaPagamento == FormaPagamento.DINHEIRO) {
            ContaReceber conta = new ContaReceber();
            conta.setDescricao(desc + " (" + formaPagamento + ")");
            conta.setValorTotal(valorTotal);
            conta.setValorLiquido(valorTotal);
            conta.setDataEmissao(LocalDate.now());
            conta.setDataVencimento(LocalDate.now());
            conta.setDataRecebimento(LocalDate.now());
            conta.setStatus(ContaReceber.StatusConta.RECEBIDO);
            conta.setFormaPagamento(formaPagamento);
            conta.setIdVendaRef(idVenda);
            contaReceberRepository.save(conta);
        }
        // 2. Recebimento D+1 (Débito ou Crédito Antecipado)
        else {
            // Calcula a data que o dinheiro vai cair (Amanhã ou Segunda-feira)
            LocalDate dataRecebimentoPrevisto = calcularProximoDiaUtil(LocalDate.now());

            BigDecimal valorParcela = valorTotal.divide(new BigDecimal(qtdParcelas), 2, RoundingMode.HALF_EVEN);
            BigDecimal diferenca = valorTotal.subtract(valorParcela.multiply(new BigDecimal(qtdParcelas)));

            for (int i = 1; i <= qtdParcelas; i++) {
                ContaReceber conta = new ContaReceber();
                conta.setDescricao(String.format("%s - Parc %d/%d (%s)", desc, i, qtdParcelas, formaPagamento));

                if (i == qtdParcelas) conta.setValorTotal(valorParcela.add(diferenca));
                else conta.setValorTotal(valorParcela);

                // Futuramente aqui aplicaremos a taxa da maquininha
                conta.setValorLiquido(conta.getValorTotal());

                conta.setDataEmissao(LocalDate.now());

                // REGRA DE OURO: Tudo vence no próximo dia útil
                conta.setDataVencimento(dataRecebimentoPrevisto);

                conta.setStatus(ContaReceber.StatusConta.PENDENTE); // Pendente até o banco confirmar
                conta.setFormaPagamento(formaPagamento);
                conta.setIdVendaRef(idVenda);

                contaReceberRepository.save(conta);
            }
        }
    }

    /**
     * Calcula o próximo dia útil (Pula Sábado e Domingo).
     */
    private LocalDate calcularProximoDiaUtil(LocalDate dataBase) {
        LocalDate proximaData = dataBase.plusDays(1); // Tenta amanhã
        DayOfWeek diaDaSemana = proximaData.getDayOfWeek();

        if (diaDaSemana == DayOfWeek.SATURDAY) {
            return proximaData.plusDays(2); // Pula Sábado e Domingo -> Segunda
        } else if (diaDaSemana == DayOfWeek.SUNDAY) {
            return proximaData.plusDays(1); // Pula Domingo -> Segunda
        }

        return proximaData; // É dia de semana normal
    }
}