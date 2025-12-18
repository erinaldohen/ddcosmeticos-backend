package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class FinanceiroService {

    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;

    /**
     * CORREÇÃO PARA A LINHA 111:
     * Lança a despesa no Contas a Pagar baseada no recebimento do pedido de compra.
     */
    @Transactional
    public void lancarDespesaDeCompra(
            Fornecedor fornecedor,
            BigDecimal valorTotal,
            String numeroNota,
            FormaPagamento forma,
            int parcelas,
            LocalDate vencimento) {

        ContaPagar conta = new ContaPagar();
        conta.setFornecedor(fornecedor);
        conta.setValorTotal(valorTotal);
        conta.setDescricao("Compra Estoque - NF: " + numeroNota);
        conta.setDataEmissao(LocalDate.now());
        conta.setDataVencimento(vencimento);
        conta.setCategoria("COMPRA_ESTOQUE");

        // Regra de Status baseada na Forma de Pagamento
        if (forma == FormaPagamento.DINHEIRO || forma == FormaPagamento.PIX) {
            conta.setStatus(StatusConta.PAGO); // Se pagou na hora, entra como PAGO
            conta.setDataPagamento(LocalDate.now());
        } else {
            conta.setStatus(StatusConta.PENDENTE); // Boletos e Cartão entram como PENDENTE
        }

        contaPagarRepository.save(conta);
    }

    /**
     * Lança automaticamente a receita originada no PDV.
     * Aplica regra de D+1 para cartões e liquidação imediata para Dinheiro/PIX.
     */
    @Transactional
    public void lancarReceitaDeVenda(Long vendaId, BigDecimal valorTotal, String formaPagamento) {
        ContaReceber titulo = new ContaReceber();
        titulo.setIdVendaRef(vendaId);
        titulo.setValorTotal(valorTotal);
        titulo.setDataEmissao(LocalDate.now());
        titulo.setFormaPagamento(formaPagamento);

        if (formaPagamento.equalsIgnoreCase("DINHEIRO") || formaPagamento.equalsIgnoreCase("PIX")) {
            titulo.setDataVencimento(LocalDate.now());
            // AQUI: Usando o seu status personalizado
            titulo.setStatus(StatusConta.RECEBIDO);
            titulo.setValorLiquido(valorTotal);
        } else {
            // Cartão: Vence em D+1 com taxa de 3%
            titulo.setDataVencimento(LocalDate.now().plusDays(1));
            titulo.setStatus(StatusConta.PENDENTE);

            BigDecimal taxa = valorTotal.multiply(new BigDecimal("0.03"));
            titulo.setValorLiquido(valorTotal.subtract(taxa));
        }

        contaReceberRepository.save(titulo);
    }

    /**
     * Lançamento manual de despesas (Aluguer, Luz, Fornecedores).
     */
    @Transactional
    public ContaPagar lancarDespesa(ContaPagar despesa) {
        despesa.setStatus(StatusConta.PENDENTE);
        if (despesa.getDataEmissao() == null) despesa.setDataEmissao(LocalDate.now());
        log.info("Nova despesa lançada: {} | Valor: {}", despesa.getDescricao(), despesa.getValorTotal());
        return contaPagarRepository.save(despesa);
    }

    /**
     * Baixa de título a pagar (Confirmação de saída de caixa).
     */
    @Transactional
    public void baixarTituloPagar(Long id) {
        ContaPagar cp = contaPagarRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a Pagar não encontrada."));

        cp.setStatus(StatusConta.PAGO);
        cp.setDataPagamento(LocalDate.now());
        contaPagarRepository.save(cp);
        log.info("Pagamento confirmado: Título #{}", id);
    }

    /**
     * Calcula o saldo projetado para uma data (Receber PENDENTE - Pagar PENDENTE).
     */
    @Transactional(readOnly = true)
    public BigDecimal calcularSaldoProjetado(LocalDate data) {
        BigDecimal aReceber = contaReceberRepository.somarAReceberPorData(data);
        BigDecimal aPagar = contaPagarRepository.somarAPagarPorData(data);

        return aReceber.subtract(aPagar);
    }

    /**
     * Lista todas as contas vencidas e não pagas.
     */
    public List<ContaPagar> listarContasVencidas() {
        return contaPagarRepository.findByDataVencimentoBeforeAndStatus(LocalDate.now(), "PENDENTE");
    }

    // Adicione estes métodos dentro da classe FinanceiroService

    @Transactional
    public void cancelarReceitaVenda(Long vendaId) {
        // Busca todos os títulos (Dinheiro, PIX ou Cartão) vinculados a essa venda
        List<ContaReceber> titulos = contaReceberRepository.findByIdVendaRef(vendaId);
        for (ContaReceber titulo : titulos) {
            titulo.setStatus(StatusConta.CANCELADO);
            contaReceberRepository.save(titulo);
        }
    }

    @Transactional
    public void ajustarReceitaPorDevolucao(Long vendaId, BigDecimal valorAbatimento) {
        List<ContaReceber> titulos = contaReceberRepository.findByIdVendaRef(vendaId);
        for (ContaReceber titulo : titulos) {
            // Subtrai o valor devolvido do total do título
            BigDecimal novoTotal = titulo.getValorTotal().subtract(valorAbatimento);
            titulo.setValorTotal(novoTotal);

            // Recalcula o líquido caso ainda esteja pendente (Cartão)
            if (titulo.getStatus() == StatusConta.PENDENTE) {
                BigDecimal taxa = novoTotal.multiply(new BigDecimal("0.03"));
                titulo.setValorLiquido(novoTotal.subtract(taxa));
            } else {
                titulo.setValorLiquido(novoTotal);
            }
            contaReceberRepository.save(titulo);
        }
    }
}