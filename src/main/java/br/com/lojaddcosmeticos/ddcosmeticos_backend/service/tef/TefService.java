package br.com.lojaddcosmeticos.ddcosmeticos_backend.service.tef;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

public interface TefService {

    /**
     * Inicia uma transação de cartão (Crédito/Débito)
     * @param valor Valor da transação
     * @param idVenda ID da venda vinculada
     * @return Token da transação ou dados do comprovante
     */
    String processarPagamentoCartao(BigDecimal valor, Long idVenda, String tipoCartao);

    /**
     * Cancela uma transação caso a nota fiscal falhe
     */
    void estornarTransacao(String nsu, BigDecimal valor);

    @Service
    @Profile("dev") // Só ativa em ambiente de desenvolvimento
    public class MockTefService implements TefService {
        @Override
        public String processarPagamentoCartao(BigDecimal valor, Long idVenda, String tipoCartao) {
            System.out.println("TEF MOCK: Processando R$ " + valor + " no " + tipoCartao);
            return "NSU-123456-MOCK"; // Retorna um código falso
        }

        @Override
        public void estornarTransacao(String nsu, BigDecimal valor) {
            System.out.println("TEF MOCK: Estornando " + nsu);
        }
    }
}