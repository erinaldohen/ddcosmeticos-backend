package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
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

    @Transactional(readOnly = true)
    public FechamentoCaixaDTO gerarResumoFechamento(LocalDate data) {
        // 1. Entradas (Recebimentos do dia)
        List<ContaReceber> recebimentos = contaReceberRepository.findAll().stream()
                .filter(c -> data.equals(c.getDataVencimento()) && StatusConta.PAGO.equals(c.getStatus()))
                .toList();

        // Calcula totais individuais
        BigDecimal totalDinheiro = somarPorTipo(recebimentos, "DINHEIRO");
        BigDecimal totalPix = somarPorTipo(recebimentos, "PIX");
        BigDecimal totalCredito = somarPorTipo(recebimentos, "CREDITO");
        BigDecimal totalDebito = somarPorTipo(recebimentos, "DEBITO");

        BigDecimal totalVendasBruto = totalDinheiro.add(totalPix).add(totalCredito).add(totalDebito);

        // Monta o MAP que o DTO exige
        Map<String, BigDecimal> mapaPagamentos = new HashMap<>();
        mapaPagamentos.put("DINHEIRO", totalDinheiro);
        mapaPagamentos.put("PIX", totalPix);
        mapaPagamentos.put("CREDITO", totalCredito);
        mapaPagamentos.put("DEBITO", totalDebito);

        // 2. Saídas (Contas Pagas no dia) - Vamos considerar como "Sangrias" ou deduções
        List<ContaPagar> pagamentos = contaPagarRepository.findAll().stream()
                .filter(c -> data.equals(c.getDataVencimento()) && StatusConta.PAGO.equals(c.getStatus()))
                .toList();

        BigDecimal totalSaidas = pagamentos.stream()
                .map(ContaPagar::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Contagem de Vendas (Aproximada pelos recebimentos únicos)
        long qtdVendas = recebimentos.stream()
                .map(ContaReceber::getIdVendaRef)
                .distinct()
                .count();

        // 4. Saldo Final (Apenas Dinheiro - Saídas, por exemplo, ou Total Geral - Saídas)
        // Ajuste conforme sua regra de negócio. Aqui fiz: (Tudo que entrou) - (Tudo que saiu)
        BigDecimal saldoFinal = totalVendasBruto.subtract(totalSaidas);

        // CORREÇÃO: Usando o BUILDER do Record
        return FechamentoCaixaDTO.builder()
                .data(data)
                .quantidadeVendas(qtdVendas)
                .totalVendasBruto(totalVendasBruto)
                .totalSuprimentos(BigDecimal.ZERO) // Implementar lógica de suprimento se houver
                .totalSangrias(totalSaidas)        // Usando as contas pagas como "saída de caixa"
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