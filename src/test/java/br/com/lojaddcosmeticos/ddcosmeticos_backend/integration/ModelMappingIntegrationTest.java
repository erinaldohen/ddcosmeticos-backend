package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PedidoCompra.StatusPedido;
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
        Produto p = new Produto();
        p.setCodigoBarras("789999999999"); // Apenas números
        p.setDescricao("PRODUTO APAGAVEL");
        p.setPrecoVenda(BigDecimal.TEN);
        p.setQuantidadeEmEstoque(0);
        p.setEstoqueFiscal(0);
        p.setEstoqueNaoFiscal(0);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);

        Produto salvo = produtoRepository.save(p);
        entityManager.flush();
        entityManager.clear();

        produtoRepository.deleteById(salvo.getId());
        entityManager.flush();

        Optional<Produto> busca = produtoRepository.findById(salvo.getId());
        Assertions.assertTrue(busca.isEmpty(), "O produto não deve ser encontrado pelo Repository (Soft Delete)");

        Number count = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM produto WHERE id = :id AND ativo = false")
                .setParameter("id", salvo.getId())
                .getSingleResult();

        Assertions.assertEquals(1, count.intValue(), "O registo deve continuar no banco marcado como false");
    }

    @Test
    @DisplayName("FORNECEDOR: Não deve permitir CNPJ duplicado (Constraint Unique)")
    public void testeFornecedorCnpjUnico() {
        Fornecedor f1 = new Fornecedor();
        f1.setRazaoSocial("Empresa A");
        f1.setCnpj("58474246000100");
        fornecedorRepository.save(f1);
        entityManager.flush();

        Fornecedor f2 = new Fornecedor();
        f2.setRazaoSocial("Empresa B");
        f2.setCnpj("58474246000100");

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
        // CORREÇÃO: Usando apenas números para evitar erro de limpeza de string no PrePersist
        p.setCodigoBarras("789123456");
        p.setDescricao("PRODUTO CASCADE");
        p.setQuantidadeEmEstoque(0);
        p.setEstoqueFiscal(0);
        p.setEstoqueNaoFiscal(0);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoVenda(BigDecimal.TEN);
        entityManager.persist(p);

        // 2. Criar Pedido com Item
        PedidoCompra pedido = new PedidoCompra();
        pedido.setFornecedorNome("Fornecedor Teste");
        pedido.setStatus(StatusPedido.EM_COTACAO);

        ItemPedidoCompra item = new ItemPedidoCompra();
        item.setProduto(p);
        item.setQuantidade(BigDecimal.TEN);
        item.setPedidoCompra(pedido);

        pedido.getItens().add(item);

        PedidoCompra salvo = pedidoCompraRepository.save(pedido);
        entityManager.flush();
        entityManager.clear();

        // 3. Validação
        PedidoCompra buscado = pedidoCompraRepository.findById(salvo.getId()).orElseThrow();
        Assertions.assertEquals(1, buscado.getItens().size(), "O item deve ter sido salvo via cascata");

        // Valide o código esperado (apenas números, conforme definido acima)
        Assertions.assertEquals("789123456", buscado.getItens().get(0).getProduto().getCodigoBarras());
    }

    @Test
    @DisplayName("CONTA RECEBER: Deve mapear Enums internos e externos corretamente")
    public void testeMapeamentoEnums() {
        ContaReceber conta = new ContaReceber();
        conta.setValorTotal(new BigDecimal("100.00"));
        conta.setValorLiquido(new BigDecimal("95.00"));
        conta.setFormaPagamento(FormaDePagamento.CREDITO.name());
        conta.setStatus(StatusConta.PENDENTE);

        ContaReceber salva = contaReceberRepository.save(conta);
        entityManager.flush();
        entityManager.clear();

        ContaReceber buscada = contaReceberRepository.findById(salva.getId()).orElseThrow();

        Assertions.assertEquals(FormaDePagamento.CREDITO.name(), buscada.getFormaPagamento());
        Assertions.assertEquals(StatusConta.PENDENTE, buscada.getStatus());
    }

    @Test
    @DisplayName("MOVIMENTO ESTOQUE: Deve validar compras com e sem nota e precisão decimal")
    public void testePrecisaoBigDecimalECompras() {
        Produto p = new Produto();
        p.setCodigoBarras("78900010001");
        p.setDescricao("TESTE DECIMAL");
        p.setQuantidadeEmEstoque(0);
        p.setEstoqueFiscal(0);
        p.setEstoqueNaoFiscal(0);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoVenda(BigDecimal.TEN);
        entityManager.persist(p);

        // --- TESTE 1: COMPRA COM NOTA (Fiscal) ---
        MovimentoEstoque mov1 = new MovimentoEstoque();
        mov1.setProduto(p);
        mov1.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        mov1.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL);
        mov1.setQuantidadeMovimentada(new BigDecimal("10"));

        BigDecimal custoDizima = new BigDecimal("10.00").divide(new BigDecimal("3.00"), 4, java.math.RoundingMode.HALF_UP);
        mov1.setCustoMovimentado(custoDizima);
        entityManager.persist(mov1);

        // --- TESTE 2: COMPRA SEM NOTA (Informal) ---
        MovimentoEstoque mov2 = new MovimentoEstoque();
        mov2.setProduto(p);
        mov2.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        mov2.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.COMPRA_SEM_NOTA_FISCAL);
        mov2.setQuantidadeMovimentada(new BigDecimal("5"));
        mov2.setCustoMovimentado(new BigDecimal("2.5000"));
        entityManager.persist(mov2);

        entityManager.flush();
        entityManager.clear();

        Object result = entityManager.getEntityManager()
                .createNativeQuery("SELECT custo_movimentado FROM movimento_estoque WHERE id = :id")
                .setParameter("id", mov1.getId())
                .getSingleResult();

        BigDecimal custoSalvo = (BigDecimal) result;
        Assertions.assertEquals(4, custoSalvo.scale(), "A escala no banco deve ser 4");
        Assertions.assertEquals(new BigDecimal("3.3333"), custoSalvo);
    }
}