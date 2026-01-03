package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CustoService {

    @Transactional
    public void atualizarCustoMedioPonderado(Produto produto, BigDecimal qtdeEntrada, BigDecimal custoEntradaUnitario) {
        BigDecimal qtdeAtual = new BigDecimal(produto.getQuantidadeEmEstoque()).subtract(qtdeEntrada); // Estoque ANTES da entrada
        BigDecimal pmpAtual = produto.getPrecoMedioPonderado();

        if (qtdeAtual.compareTo(BigDecimal.ZERO) < 0) { // Primeira entrada ou estoque zerado
            produto.setPrecoMedioPonderado(custoEntradaUnitario);
            return;
        }

        BigDecimal valorTotalAntigo = qtdeAtual.multiply(pmpAtual);
        BigDecimal valorTotalEntrada = qtdeEntrada.multiply(custoEntradaUnitario);

        BigDecimal novaQtdeTotal = qtdeAtual.add(qtdeEntrada);

        if (novaQtdeTotal.compareTo(BigDecimal.ZERO) <= 0) {
            produto.setPrecoMedioPonderado(BigDecimal.ZERO); // Evitar divisão por zero se estoque ficar zerado
        } else {
            BigDecimal novoPMP = valorTotalAntigo.add(valorTotalEntrada).divide(novaQtdeTotal, 4, RoundingMode.HALF_UP);
            produto.setPrecoMedioPonderado(novoPMP);
        }
    }
}