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
        // Prod A: 5 und * 10.00 = 50.00
        criarProduto("Prod A", new BigDecimal("10.00"), 5);
        // Prod B: 2 und * 20.00 = 40.00
        criarProduto("Prod B", new BigDecimal("20.00"), 2);

        // Total esperado: 90.00

        // Ação
        BigDecimal total = produtoRepository.calcularValorTotalEstoque();

        // Verificação
        assertThat(total).isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("Deve buscar Top produtos mais caros")
    void deveBuscarTop50() {
        // Cenário: Cria 60 produtos com preços crescentes
        for (int i = 0; i < 60; i++) {
            // O preço será i+1. O último (i=59) custará 60.00 e chamará "PROD 59"
            criarProduto("Prod " + i, new BigDecimal(i + 1), 10);
        }

        // Ação
        List<Produto> topProdutos = produtoRepository.findTop10ByOrderByPrecoVendaDesc();

        // Verificação
        assertThat(topProdutos).isNotEmpty();
        assertThat(topProdutos).hasSize(10); // Garante que limitou a 10

        // O primeiro deve ser o mais caro ("PROD 59" convertido para maiúsculo pelo PrePersist)
        assertThat(topProdutos.get(0).getDescricao()).isEqualTo("PROD 59");
    }

    @Test
    @DisplayName("Deve listar produtos com baixo estoque")
    void deveListarProdutosAbaixoMinimo() {
        // Cenário
        Produto p1 = criarProduto("Baixo 1", BigDecimal.TEN, 2);
        p1.setEstoqueMinimo(5); // 2 < 5 (Entra na lista)
        entityManager.persistAndFlush(p1);

        Produto p2 = criarProduto("Baixo 2", BigDecimal.TEN, 0);
        p2.setEstoqueMinimo(5); // 0 < 5 (Entra na lista)
        entityManager.persistAndFlush(p2);

        Produto p3 = criarProduto("Normal", BigDecimal.TEN, 10);
        p3.setEstoqueMinimo(5); // 10 > 5 (Não entra)
        entityManager.persistAndFlush(p3);

        // Ação
        List<Produto> abaixo = produtoRepository.findProdutosComBaixoEstoque();

        // Verificação
        assertThat(abaixo).hasSize(2);
        // Verifica se os produtos retornados são realmente os que tem baixo estoque
        assertThat(abaixo).extracting(Produto::getDescricao)
                .containsExactlyInAnyOrder("BAIXO 1", "BAIXO 2");
    }

    // --- MÉTODOS AUXILIARES ---

    private Produto criarProduto(String nome, BigDecimal precoCusto, Integer quantidade) {
        Produto produto = new Produto();
        produto.setDescricao(nome); // O @PrePersist vai converter para Uppercase

        // CORREÇÃO CRÍTICA: Gerar código APENAS NUMÉRICO e ÚNICO.
        // O @PrePersist da entidade remove letras. Se passar "EAN-...", vira vazio.
        long eanUnico = System.nanoTime() + (long)(Math.random() * 100000);
        produto.setCodigoBarras(String.valueOf(eanUnico));

        produto.setPrecoCusto(precoCusto);
        produto.setPrecoMedioPonderado(precoCusto); // Inicializa para não dar erro de null em cálculos
        produto.setPrecoVenda(precoCusto.multiply(new BigDecimal("2.0")));

        // Configuração de Estoque (Fiscal + Não Fiscal = Total)
        produto.setEstoqueNaoFiscal(quantidade);
        produto.setEstoqueFiscal(0);
        // Não precisamos setar quantidadeEmEstoque manualmente pois o @PrePersist calcula

        produto.setNcm("33049990"); // NCM válido de cosmético
        produto.setAtivo(true);

        return entityManager.persist(produto);
    }
}