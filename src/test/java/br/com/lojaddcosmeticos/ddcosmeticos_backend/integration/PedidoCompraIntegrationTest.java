package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.PedidoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PedidoCompra;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.PedidoCompraRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PedidoCompraService;
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
public class PedidoCompraIntegrationTest {

    @Autowired private PedidoCompraService pedidoService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private PedidoCompraRepository pedidoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private FornecedorRepository fornecedorRepository;

    @Test
    @DisplayName("Simulação Interestadual: Deve aplicar ICMS e ST")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeSimulacaoCompraInterestadual() {
        criarFornecedor("Fornecedor SP", "SP");
        criarProduto("7891234567890", new BigDecimal("100.00")); // CORREÇÃO: Preço de venda definido

        PedidoCompraDTO dto = new PedidoCompraDTO();
        dto.setFornecedorNome("Fornecedor SP");
        dto.setUfOrigem("SP");
        dto.setUfDestino("PE"); // PE = 18%, SP->PE = 7%

        ItemCompraDTO item = new ItemCompraDTO();
        item.setCodigoBarras("7891234567890");
        item.setQuantidade(new BigDecimal("10"));
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setMva(new BigDecimal("60.00")); // MVA Ajustada

        dto.setItens(List.of(item));

        PedidoCompra pedido = pedidoService.criarSimulacao(dto);

        Assertions.assertNotNull(pedido.getId());
        Assertions.assertTrue(pedido.getTotalImpostosEstimados().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Simulação Local: Sem ST, apenas crédito de ICMS")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeSimulacaoCompraLocal() {
        criarFornecedor("Fornecedor PE", "PE");
        criarProduto("7891234567891", new BigDecimal("100.00"));

        PedidoCompraDTO dto = new PedidoCompraDTO();
        dto.setFornecedorNome("Fornecedor PE");
        dto.setUfOrigem("PE");
        dto.setUfDestino("PE");

        ItemCompraDTO item = new ItemCompraDTO();
        item.setCodigoBarras("7891234567891");
        item.setQuantidade(new BigDecimal("10"));
        item.setPrecoUnitario(new BigDecimal("50.00"));

        dto.setItens(List.of(item));

        PedidoCompra pedido = pedidoService.criarSimulacao(dto);
        Assertions.assertTrue(pedido.getTotalImpostosEstimados().compareTo(BigDecimal.ZERO) == 0);
    }

    private void criarFornecedor(String nome, String uf) {
        Fornecedor f = new Fornecedor();
        f.setRazaoSocial(nome);
        // CORREÇÃO: 14 dígitos exatos (000.000.000-001/02)
        f.setCpfOuCnpj("0000000000000" + (uf.equals("SP") ? "1" : "2"));
        f.setTipoPessoa("JURIDICA");
        f.setAtivo(true);
        fornecedorRepository.save(f);
    }

    private void criarProduto(String ean, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setCodigoBarras(ean);
        p.setDescricao("PRODUTO " + ean);
        p.setPrecoVenda(precoVenda); // OBRIGATÓRIO
        p.setEstoqueFiscal(0);
        p.setEstoqueNaoFiscal(0);
        p.setQuantidadeEmEstoque(0);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setAtivo(true);
        produtoRepository.save(p);
    }
}