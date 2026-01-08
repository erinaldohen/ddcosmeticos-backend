package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test") // Garante que usa H2
class ProdutoRepositoryTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Test
    @DisplayName("Deve calcular o valor total do estoque (Custo * Quantidade) corretamente via SQL")
    void deveCalcularValorTotalEstoque() {
        // Cenário
        criarProduto("Shampoo A", new BigDecimal("10.00"), 5); // Total: 50.00
        criarProduto("Creme B", new BigDecimal("20.00"), 2);   // Total: 40.00
        criarProduto("Inativo", new BigDecimal("100.00"), 10, false); // Não deve contar

        // Ação
        BigDecimal total = produtoRepository.calcularValorTotalEstoque();

        // Validação
        assertThat(total).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("Deve contar produtos com estoque baixo ou zerado")
    void deveContarProdutosAbaixoMinimo() {
        // Cenário (Minimo padrão é 5 na criação auxiliar abaixo)
        criarProduto("Baixo 1", BigDecimal.TEN, 3); // < 5 (Conta)
        criarProduto("Zerado", BigDecimal.TEN, 0);  // < 5 (Conta)
        criarProduto("Normal", BigDecimal.TEN, 10); // > 5 (Não conta)

        // Ação
        Long conta = produtoRepository.contarProdutosAbaixoDoMinimo();

        // Validação
        assertThat(conta).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve encontrar top 50 produtos ativos")
    void deveBuscarTop50() {
        for (int i = 0; i < 60; i++) {
            criarProduto("Prod " + i, BigDecimal.ONE, 10);
        }

        var lista = produtoRepository.findTop50ByAtivoTrueOrderByIdDesc();

        assertThat(lista).hasSize(50);
        // Garante que o primeiro é o último criado (ID mais alto)
        assertThat(lista.get(0).getDescricao()).isEqualTo("Prod 59");
    }

    // Método auxiliar para popular o banco em memória
    private void criarProduto(String nome, BigDecimal custo, Integer qtd, boolean ativo) {
        Produto p = new Produto();
        p.setDescricao(nome);
        p.setPrecoCusto(custo);
        p.setPrecoVenda(custo.multiply(new BigDecimal("2.0")));
        p.setQuantidadeEmEstoque(qtd);
        p.setEstoqueMinimo(5);
        p.setAtivo(ativo);
        p.setCodigoBarras("EAN-" + nome);
        produtoRepository.save(p);
    }

    private void criarProduto(String nome, BigDecimal custo, Integer qtd) {
        criarProduto(nome, custo, qtd, true);
    }
}