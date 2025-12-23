package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class EstoqueIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;

    @Test
    @DisplayName("ENTRADA: Deve atualizar estoque, calcular custo médio e gerar financeiro")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeEntradaComFinanceiro() {
        // 1. Preparação: Produto com estoque zerado
        String codigoBarras = "789100010001";
        criarProduto(codigoBarras, new BigDecimal("100.00")); // Preço de venda R$ 100

        // 2. Ação: Entrada de 10 unidades a R$ 50,00 cada
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(codigoBarras);
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(new BigDecimal("50.00"));
        dto.setNumeroNotaFiscal("NF-1234");
        dto.setFornecedorCnpj("00.000.000/0001-00");
        dto.setFormaPagamento(FormaDePagamento.BOLETO);
        dto.setQuantidadeParcelas(1);

        estoqueService.registrarEntrada(dto);

        // 3. Validação do Produto (Estoque e Custo)
        Produto produtoAtualizado = produtoRepository.findByCodigoBarras(codigoBarras).orElseThrow();

        // Verifica Quantidade: 0 + 10 = 10
        Assertions.assertEquals(0, new BigDecimal("10.000").compareTo(produtoAtualizado.getQuantidadeEmEstoque()));

        // Verifica Custo Médio: (0*0 + 10*50) / 10 = 50.00
        Assertions.assertEquals(0, new BigDecimal("50.0000").compareTo(produtoAtualizado.getPrecoMedioPonderado()));

        // 4. Validação do Histórico (Kardex)
        List<MovimentoEstoque> movimentos = movimentoEstoqueRepository.findAll();
        Assertions.assertFalse(movimentos.isEmpty());
        MovimentoEstoque mov = movimentos.get(0);
        Assertions.assertEquals("ENTRADA", mov.getTipoMovimento());
        Assertions.assertEquals(0, new BigDecimal("10.000").compareTo(mov.getQuantidadeMovimentada()));

        // 5. Validação Financeira
        List<ContaPagar> contas = contaPagarRepository.findAll();
        Assertions.assertFalse(contas.isEmpty());
        ContaPagar conta = contas.get(0);
        Assertions.assertEquals(StatusConta.PENDENTE, conta.getStatus()); // Boleto é pendente
        Assertions.assertEquals(0, new BigDecimal("500.00").compareTo(conta.getValorTotal())); // 10 * 50
    }

    @Test
    @DisplayName("AJUSTE: Deve reduzir estoque em caso de PERDA/QUEBRA")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeAjusteInventarioSaida() {
        // 1. Preparação: Produto com 20 unidades em estoque
        String codigoBarras = "789100020002";
        Produto p = criarProduto(codigoBarras, new BigDecimal("50.00"));
        p.setQuantidadeEmEstoque(new BigDecimal("20.000"));
        p.setPrecoMedioPonderado(new BigDecimal("25.0000")); // Custo de 25
        produtoRepository.save(p);

        // 2. Ação: Registrar quebra de 2 unidades
        AjusteEstoqueDTO dto = new AjusteEstoqueDTO();
        dto.setCodigoBarras(codigoBarras);
        dto.setQuantidade(new BigDecimal("2"));
        dto.setTipoMovimento("PERDA");
        dto.setMotivo("Produto danificado na prateleira");

        estoqueService.realizarAjusteInventario(dto);

        // 3. Validação do Estoque
        Produto produtoAtualizado = produtoRepository.findByCodigoBarras(codigoBarras).orElseThrow();
        // 20 - 2 = 18
        Assertions.assertEquals(0, new BigDecimal("18.000").compareTo(produtoAtualizado.getQuantidadeEmEstoque()));

        // 4. Validação do Histórico
        MovimentoEstoque mov = movimentoEstoqueRepository.findAll().stream()
                .filter(m -> m.getProduto().getCodigoBarras().equals(codigoBarras))
                .findFirst().orElseThrow();

        Assertions.assertEquals("PERDA", mov.getTipoMovimento());
        Assertions.assertEquals(0, new BigDecimal("2.000").compareTo(mov.getQuantidadeMovimentada()));
        // O custo movimentado deve ser o PMP atual (25.00)
        Assertions.assertEquals(0, new BigDecimal("25.0000").compareTo(mov.getCustoMovimentado()));
    }

    // --- MÉTODOS AUXILIARES ---

    private Produto criarProduto(String codigo, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("PRODUTO TESTE INTEGRACAO " + codigo);
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setPrecoVenda(precoVenda);

        // --- CORREÇÃO IMPORTANTE: Inicializa custos com ZERO para evitar NullPointerException ---
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCustoInicial(BigDecimal.ZERO);
        // --------------------------------------------------------------------------------------

        p.setAtivo(true);
        p.setPossuiNfEntrada(true);
        return produtoRepository.save(p);
    }
}