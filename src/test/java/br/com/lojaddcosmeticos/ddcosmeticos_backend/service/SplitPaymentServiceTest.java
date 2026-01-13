package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SplitPaymentInstructionDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SplitPaymentServiceTest {

    private final SplitPaymentService splitPaymentService = new SplitPaymentService();

    @Test
    @DisplayName("Deve garantir que a soma das partes do Split é igual ao total líquido da venda")
    void deveGarantirIntegridadeDoSplit() {
        // 1. CENÁRIO (Setup)
        Usuario vendedor = new Usuario();
        vendedor.setMatricula("VEND-001");

        Venda venda = new Venda();
        venda.setUsuario(vendedor);
        venda.setTotalVenda(new BigDecimal("100.00"));
        venda.setDescontoTotal(new BigDecimal("10.00")); // Total líquido: 90.00

        // Produto sujeito a reforma e imposto seletivo
        Produto p = new Produto();
        p.setImpostoSeletivo(true);

        ItemVenda item = new ItemVenda();
        item.setProduto(p);
        item.setQuantidade(new BigDecimal("1"));
        item.setPrecoUnitario(new BigDecimal("100.00"));

        // Alíquotas simuladas da Reforma (ex: 10% IBS, 15% CBS)
        item.setAliquotaIbsAplicada(new BigDecimal("0.10"));
        item.setAliquotaCbsAplicada(new BigDecimal("0.15"));
        item.setValorImpostoSeletivo(new BigDecimal("5.00"));

        venda.adicionarItem(item);

        // 2. EXECUÇÃO (Action)
        List<SplitPaymentInstructionDTO> instrucoes = splitPaymentService.gerarInstrucoesSplit(venda);

        // 3. VALIDAÇÃO (Assertion)
        BigDecimal somaDasPartes = instrucoes.stream()
                .map(SplitPaymentInstructionDTO::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorEsperado = venda.getTotalVenda().subtract(venda.getDescontoTotal());

        assertEquals(0, valorEsperado.compareTo(somaDasPartes),
                "A soma do Split (R$" + somaDasPartes + ") deve ser igual ao valor líquido da venda (R$" + valorEsperado + ")");

        // Validar se existem exatamente 3 instruções (União, Estado, Lojista)
        assertEquals(3, instrucoes.size());
    }
}