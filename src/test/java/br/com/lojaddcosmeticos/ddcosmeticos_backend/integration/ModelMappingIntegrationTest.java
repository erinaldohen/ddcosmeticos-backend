package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class ModelMappingIntegrationTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private PedidoCompraRepository pedidoCompraRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;

    @Test
    @DisplayName("PRODUTO: Deve aplicar Soft Delete (@SQLDelete) e não apagar registo físico")
    public void testeProdutoSoftDelete() {
        // 1. Criar e Persistir
        Produto p = new Produto();
        p.setCodigoBarras("789_SOFT_DELETE");
        p.setDescricao("PRODUTO APAGAVEL");
        p.setPrecoVenda(BigDecimal.TEN);
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setPrecoCustoInicial(BigDecimal.ZERO);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);

        Produto salvo = produtoRepository.save(p);
        entityManager.flush();
        entityManager.clear();

        // 2. Apagar
        produtoRepository.deleteById(salvo.getId());
        entityManager.flush();

        // 3. Verificar se o JPA não encontra mais (devido ao @SQLRestriction)
        Optional<Produto> busca = produtoRepository.findById(salvo.getId());
        Assertions.assertTrue(busca.isEmpty(), "O produto não deve ser encontrado pelo Repository (Soft Delete)");

        // 4. Verificar via SQL Nativo se ainda existe no banco com ativo=false
        // CORREÇÃO: Usar 'Number' para evitar ClassCastException (H2 retorna Long)
        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM produto WHERE id = :id AND ativo = false")
                .setParameter("id", salvo.getId())
                .getSingleResult();

        Assertions.assertEquals(1, count.intValue(), "O registo deve continuar no banco marcado como false");
    }

    @Test
    @DisplayName("FORNECEDOR: Não deve permitir CNPJ duplicado (Constraint Unique)")
    public void testeFornecedorCnpjUnico() {
        // Fornecedor 1
        Fornecedor f1 = new Fornecedor();
        f1.setRazaoSocial("Empresa A");
        f1.setCpfOuCnpj("00000000000100");
        fornecedorRepository.save(f1);
        entityManager.flush();

        // Fornecedor 2 (Mesmo CNPJ)
        Fornecedor f2 = new Fornecedor();
        f2.setRazaoSocial("Empresa B");
        f2.setCpfOuCnpj("00000000000100"); // Duplicado

        // Deve lançar exceção de integridade de dados
        Assertions.assertThrows(DataIntegrityViolationException.class, () -> {
            fornecedorRepository.save(f2);
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("PEDIDO COMPRA: Deve salvar ITENS em cascata (CascadeType.ALL)")
    public void testeCascadePedidoItens() {
        // 1. Criar Produto
        Produto p = new Produto();
        p.setCodigoBarras("789_CASCADE");
        p.setDescricao("PRODUTO CASCADE");
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setPrecoCustoInicial(BigDecimal.ZERO);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoVenda(BigDecimal.TEN);
        entityManager.persist(p);

        // 2. Criar Pedido
        PedidoCompra pedido = new PedidoCompra();
        pedido.setFornecedorNome("Fornecedor Teste");
        pedido.setStatus(PedidoCompra.StatusPedido.EM_COTACAO);

        // 3. Adicionar Item ao Pedido
        ItemPedidoCompra item = new ItemPedidoCompra();
        item.setProduto(p);
        item.setQuantidade(BigDecimal.TEN);
        item.setPedidoCompra(pedido); // Vínculo bidirecional importante

        pedido.getItens().add(item); // Adiciona na lista

        // 4. Salvar APENAS o Pedido
        PedidoCompra salvo = pedidoCompraRepository.save(pedido);
        entityManager.flush();
        entityManager.clear();

        // 5. Verificar se o Item foi salvo automaticamente
        PedidoCompra buscado = pedidoCompraRepository.findById(salvo.getId()).orElseThrow();
        Assertions.assertEquals(1, buscado.getItens().size(), "O item deve ter sido salvo via cascata");
        Assertions.assertEquals("789_CASCADE", buscado.getItens().get(0).getProduto().getCodigoBarras());
    }

    @Test
    @DisplayName("CONTA RECEBER: Deve mapear Enums internos e externos corretamente")
    public void testeMapeamentoEnums() {
        ContaReceber conta = new ContaReceber();
        conta.setDescricao("Teste Enum");
        conta.setValorTotal(new BigDecimal("100.00"));
        conta.setValorLiquido(new BigDecimal("95.00"));

        // Testando Enum FormaPagamento (Externo)
        conta.setFormaPagamento(FormaPagamento.CREDITO);

        // Testando Enum StatusConta (Interno da classe ContaReceber)
        conta.setStatus(ContaReceber.StatusConta.PENDENTE);

        ContaReceber salva = contaReceberRepository.save(conta);
        entityManager.flush();
        entityManager.clear();

        ContaReceber buscada = contaReceberRepository.findById(salva.getId()).orElseThrow();

        Assertions.assertEquals(FormaPagamento.CREDITO, buscada.getFormaPagamento());
        Assertions.assertEquals(ContaReceber.StatusConta.PENDENTE, buscada.getStatus());
    }

    @Test
    @DisplayName("MOVIMENTO ESTOQUE: Deve persistir precisão correta do custo (4 casas decimais)")
    public void testePrecisaoBigDecimal() {
        Produto p = new Produto();
        p.setCodigoBarras("789_DECIMAL");
        p.setDescricao("TESTE DECIMAL");
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setPrecoCustoInicial(BigDecimal.ZERO);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoVenda(BigDecimal.TEN);
        entityManager.persist(p);

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(p);
        mov.setTipoMovimento("ENTRADA");
        mov.setQuantidadeMovimentada(new BigDecimal("1.555"));

        // Teste de precisão: 10/3 = 3.33333... deve salvar como 3.3333
        BigDecimal custoDizima = new BigDecimal("10.00").divide(new BigDecimal("3.00"), 4, java.math.RoundingMode.HALF_UP);
        mov.setCustoMovimentado(custoDizima);

        entityManager.persist(mov);
        entityManager.flush();
        entityManager.clear();

        // Validação via SQL nativo
        Object result = entityManager.getEntityManager()
                .createNativeQuery("SELECT custo_movimentado FROM movimento_estoque WHERE id = :id")
                .setParameter("id", mov.getId())
                .getSingleResult();

        BigDecimal custoSalvo = (BigDecimal) result;
        // Espera-se 3.3333 (escala 4)
        Assertions.assertEquals(4, custoSalvo.scale(), "A escala no banco deve ser 4");
        Assertions.assertEquals(new BigDecimal("3.3333"), custoSalvo);
    }
}