package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProdutoRepositoryTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Deve calcular valor total do estoque corretamente")
    void deveCalcularValorTotalEstoque() {
        // Cenário
        criarProduto("Prod A", new BigDecimal("10.00"), 5); // 5 * 10 = 50
        criarProduto("Prod B", new BigDecimal("20.00"), 2); // 2 * 20 = 40
        // Total esperado: 90.00

        // Ação: Agora usando o método real do repositório
        BigDecimal total = produtoRepository.calcularValorTotalEstoque();

        // Verificação
        assertThat(total).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("Deve buscar Top produtos mais caros (Correção Case Sensitive)")
    void deveBuscarTop50() {
        // Cenário
        for (int i = 0; i < 60; i++) {
            // A entidade converte para "PROD X" automaticamente agora
            criarProduto("Prod " + i, new BigDecimal(i + 1), 10);
        }

        // Ação
        // Ajuste o método abaixo para o que existe no seu repositório (ex: findTop10ByOrderByPrecoVendaDesc)
        List<Produto> topProdutos = produtoRepository.findTop10ByOrderByPrecoVendaDesc();

        // Verificação
        // Agora esperamos "PROD 59" (Maiúsculo) por causa da regra do @PrePersist
        assertThat(topProdutos).isNotEmpty();
        assertThat(topProdutos.get(0).getDescricao()).isEqualTo("PROD 59");
    }

    @Test
    @DisplayName("Deve contar produtos abaixo do estoque mínimo")
    void deveContarProdutosAbaixoMinimo() {
        // Cenário
        Produto p1 = criarProduto("Baixo 1", BigDecimal.TEN, 2);
        p1.setEstoqueMinimo(5); // 2 < 5 (Conta)
        entityManager.persistAndFlush(p1);

        Produto p2 = criarProduto("Baixo 2", BigDecimal.TEN, 0);
        p2.setEstoqueMinimo(5); // 0 < 5 (Conta)
        entityManager.persistAndFlush(p2);

        Produto p3 = criarProduto("Normal", BigDecimal.TEN, 10);
        p3.setEstoqueMinimo(5); // 10 > 5 (Não conta)
        entityManager.persistAndFlush(p3);

        // Ação
        // Ajuste para o nome real do seu método no Repository
        List<Produto> abaixo = produtoRepository.findProdutosComBaixoEstoque();

        // Verificação
        assertThat(abaixo).hasSize(2); // Espera 2, não 3
    }

    // --- MÉTODOS AUXILIARES ---

    private Produto criarProduto(String nome, BigDecimal precoCusto, Integer quantidade) {
        Produto produto = new Produto();
        produto.setDescricao(nome);
        produto.setCodigoBarras("EAN-" + nome.replaceAll(" ", ""));
        produto.setPrecoCusto(precoCusto);
        produto.setPrecoVenda(precoCusto.multiply(new BigDecimal("2.0"))); // Venda = 2x Custo

        // --- CORREÇÃO FUNDAMENTAL ---
        // Não usamos setQuantidadeEmEstoque, pois o @PrePersist vai zerar.
        // Usamos setEstoqueNaoFiscal (ou Fiscal)
        produto.setEstoqueNaoFiscal(quantidade);
        produto.setEstoqueFiscal(0);

        // Define NCM para passar na validação fiscal
        produto.setNcm("33049990");

        return entityManager.persist(produto);
    }
}