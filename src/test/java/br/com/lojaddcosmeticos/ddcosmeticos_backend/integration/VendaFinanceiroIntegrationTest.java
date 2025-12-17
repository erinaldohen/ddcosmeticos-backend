package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class VendaFinanceiroIntegrationTest {

    @Autowired private VendaService vendaService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;

    @Test
    @DisplayName("Venda CRÉDITO 3x: Deve gerar 3 registros, mas TODOS vencendo no PRÓXIMO DIA ÚTIL")
    @WithMockUser(username = "caixa", roles = {"CAIXA"})
    public void testeVendaCreditoAntecipado() {
        // 1. Setup: Produto de R$ 100,00
        Produto p = new Produto();
        p.setCodigoBarras("789_PERFUME_TOP");
        p.setDescricao("PERFUME CARO");
        p.setPrecoVenda(new BigDecimal("100.00"));
        p.setQuantidadeEmEstoque(new BigDecimal("10"));
        p.setAtivo(true);
        produtoRepository.save(p);

        // 2. Venda: 3 unidades = R$ 300,00 (Parcelado em 3x)
        VendaRequestDTO dto = new VendaRequestDTO();
        dto.setFormaPagamento(FormaPagamento.CREDITO);
        dto.setQuantidadeParcelas(3);

        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodigoBarras("789_PERFUME_TOP");
        item.setQuantidade(new BigDecimal("3"));
        dto.setItens(List.of(item));

        // 3. Execução
        Venda vendaSalva = vendaService.realizarVenda(dto);

        // 4. Validações

        // Estoque baixou?
        Produto pAtualizado = produtoRepository.findByCodigoBarras("789_PERFUME_TOP").get();
        Assertions.assertTrue(new BigDecimal("7.000").compareTo(pAtualizado.getQuantidadeEmEstoque()) == 0);

        // Financeiro Gerado?
        List<ContaReceber> recebiveis = contaReceberRepository.findByIdVendaRef(vendaSalva.getId());
        Assertions.assertEquals(3, recebiveis.size(), "Devem existir 3 registros de rastreio das parcelas");

        // A REGRA DE OURO: Data de Vencimento
        // A data deve ser > hoje (D+1 ou mais se for fim de semana)
        LocalDate hoje = LocalDate.now();

        for (ContaReceber conta : recebiveis) {
            Assertions.assertTrue(conta.getDataVencimento().isAfter(hoje),
                    "O recebimento deve ser futuro (D+1), não hoje.");

            // Todas as parcelas devem vencer no MESMO DIA (Antecipação)
            Assertions.assertEquals(recebiveis.get(0).getDataVencimento(), conta.getDataVencimento(),
                    "Todas as parcelas devem cair na conta juntas (Antecipação Total)");

            Assertions.assertTrue(new BigDecimal("100.00").compareTo(conta.getValorTotal()) == 0,
                    "O valor da parcela deve ser R$ 100,00");

            Assertions.assertEquals(ContaReceber.StatusConta.PENDENTE, conta.getStatus(),
                    "O status deve ser PENDENTE até cair na conta");
        }

        System.out.println(">>> SUCESSO: Venda parcelada com antecipação validada!");
    }
}