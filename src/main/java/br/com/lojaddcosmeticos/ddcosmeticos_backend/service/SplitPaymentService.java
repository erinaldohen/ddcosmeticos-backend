package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SplitPaymentInstructionDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SplitPaymentService {

    /**
     * Calcula as instruções de divisão de pagamento (Split) para gateways como Pagar.me ou Ebanx.
     * Fundamental para a "Lei do Salão Parceiro" ou comissões automáticas.
     */
    // Renomeado para 'gerarInstrucoesSplit' para compatibilidade com PagamentoGatewayService
    public List<SplitPaymentInstructionDTO> gerarInstrucoesSplit(Venda venda) {
        List<SplitPaymentInstructionDTO> instrucoes = new ArrayList<>();

        if (venda.getItens() == null) {
            return instrucoes;
        }

        for (ItemVenda item : venda.getItens()) {
            // [CORREÇÃO] Cálculo manual pois item.getTotalItem() não existe na entidade
            BigDecimal totalItem = (item.getPrecoUnitario() != null && item.getQuantidade() != null)
                    ? item.getPrecoUnitario().multiply(item.getQuantidade())
                    : BigDecimal.ZERO;

            // Lógica de negócio futura para parceiros...
            // Ex: if (item.getProduto().getParceiro() != null) { ... }
        }

        return instrucoes;
    }
}