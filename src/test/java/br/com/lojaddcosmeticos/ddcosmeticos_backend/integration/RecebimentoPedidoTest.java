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
public class RecebimentoPedidoTest {

    @Autowired private PedidoCompraService pedidoService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private PedidoCompraRepository pedidoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private FornecedorRepository fornecedorRepository;

    @Test
    @DisplayName("Deve receber um pedido, atualizar estoque e gerar conta a pagar consolidada")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeRecebimentoPedidoCompleto() {
        // 1. Setup: Fornecedor e Produto
        Fornecedor f = new Fornecedor();
        f.setRazaoSocial("Natura Distribuidora");
        f.setCpfOuCnpj("12345678000100");
        f.setTipoPessoa("JURIDICA");
        f.setAtivo(true);
        fornecedorRepository.save(f);

        Produto p = new Produto();
        p.setCodigoBarras("789_KAIK_AVENTURA");
        p.setDescricao("PERFUME KAIK");
        p.setQuantidadeEmEstoque(0);
        p.setPrecoVenda(new BigDecimal("150.00"));
        p.setAtivo(true);
        p.setPossuiNfEntrada(true);

        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCusto(BigDecimal.ZERO);
        produtoRepository.save(p);

        // 2. Criação do Pedido
        PedidoCompraDTO dto = new PedidoCompraDTO();
        dto.setFornecedorNome("Natura Distribuidora");
        dto.setUfOrigem("SP");
        dto.setUfDestino("PE");

        ItemCompraDTO item = new ItemCompraDTO();
        item.setCodigoBarras("789_KAIK_AVENTURA");
        item.setQuantidade(new BigDecimal("10"));
        item.setPrecoUnitario(new BigDecimal("80.00"));
        item.setMva(new BigDecimal("50.00"));
        dto.setItens(List.of(item));

        PedidoCompra pedidoSalvo = pedidoService.criarSimulacao(dto);
        Long idPedido = pedidoSalvo.getId();

        // CORREÇÃO: Acessando o Enum através da classe PedidoCompra
        Assertions.assertEquals(PedidoCompra.StatusPedido.EM_COTACAO, pedidoSalvo.getStatus());

        // 3. AÇÃO: Receber Mercadoria
        pedidoService.receberMercadoria(idPedido, "NF-555", LocalDate.now().plusDays(30));

        // 4. Validações

        // A. Status do Pedido
        PedidoCompra pedidoAtualizado = pedidoRepository.findById(idPedido).get();
        // CORREÇÃO: Acessando o Enum através da classe PedidoCompra
        Assertions.assertEquals(PedidoCompra.StatusPedido.CONCLUIDO, pedidoAtualizado.getStatus());

        // B. Estoque Atualizado
        Produto produtoEstoque = produtoRepository.findByCodigoBarras("789_KAIK_AVENTURA").get();
        Assertions.assertEquals(10, produtoEstoque.getQuantidadeEmEstoque());

        // C. Financeiro Gerado
        List<ContaPagar> contas = contaPagarRepository.findAll();
        Assertions.assertFalse(contas.isEmpty(), "Deve ter gerado contas a pagar");

        ContaPagar conta = contas.stream()
                .filter(c -> c.getDescricao() != null && c.getDescricao().contains("NF-555"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Conta não encontrada"));

        // O valor deve ser o total final do pedido
        Assertions.assertEquals(0, pedidoAtualizado.getTotalFinal().compareTo(conta.getValorTotal()));
        Assertions.assertEquals("Natura Distribuidora", conta.getFornecedor().getRazaoSocial());

        System.out.println(">>> SUCESSO: Pedido transformado em Estoque e Dívida!");
    }
}