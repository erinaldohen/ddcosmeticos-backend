package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class FinanceiroService {

    @Autowired
    private ContaPagarRepository contaPagarRepository;

    /**
     * Cria uma conta a pagar automaticamente a partir de uma entrada de estoque.
     */
    public void lancarDespesaDeCompra(Fornecedor fornecedor,
                                      BigDecimal valorTotal,
                                      String numeroNota,
                                      LocalDate dataVencimento) {

        ContaPagar conta = new ContaPagar();
        conta.setFornecedor(fornecedor);
        conta.setDescricao("Compra de Estoque - NF: " + (numeroNota != null ? numeroNota : "S/N"));
        conta.setValorTotal(valorTotal);
        conta.setDataEmissao(LocalDate.now());

        // Se não informar vencimento, joga para 30 dias (Regra de Negócio Padrão)
        conta.setDataVencimento(dataVencimento != null ? dataVencimento : LocalDate.now().plusDays(30));

        conta.setStatus(ContaPagar.StatusConta.PENDENTE);

        contaPagarRepository.save(conta);
        System.out.println(">>> FINANCEIRO: Conta a Pagar gerada para " + fornecedor.getRazaoSocial());
    }
}