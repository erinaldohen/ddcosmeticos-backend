package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CalculadoraFiscalService;
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

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class TributacaoIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CalculadoraFiscalService calculadoraFiscalService;

    @Test
    @DisplayName("SHAMPOO (33051000): Deve ser Monofásico (Regra de Negócio)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeClassificacaoShampoo() {
        String ean = "7891111111111";
        criarProduto(ean, "33051000", "Shampoo Normal");

        Produto p = produtoRepository.findByCodigoBarras(ean).get();

        // CORREÇÃO: O serviço define 3305... como Monofásico. O teste deve esperar true.
        Assertions.assertTrue(p.isMonofasico(), "Shampoo (3305) deve ser classificado como Monofásico");
    }

    @Test
    @DisplayName("SABONETE (34011190): Deve ser Monofásico (500)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeClassificacaoSabonete() {
        String ean = "7892222222222";
        criarProduto(ean, "34011190", "Sabonete Líquido");

        Produto p = produtoRepository.findByCodigoBarras(ean).get();
        Assertions.assertTrue(p.isMonofasico());
    }

    @Test
    @DisplayName("Entrada de PJ: Deve registrar como estoque fiscal")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeEntradaFornecedorJuridica() {
        String ean = "7893333333333";
        criarProduto(ean, "33049990", "Produto Teste PJ");

        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(ean);
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(new BigDecimal("20.00"));
        dto.setNumeroNotaFiscal("12345");
        dto.setFornecedorCnpj("12345678000199");
        dto.setFormaPagamento(br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento.DINHEIRO); // Adicionado para gerar financeiro
        dto.setQuantidadeParcelas(1);

        estoqueService.registrarEntrada(dto);

        Produto p = produtoRepository.findByCodigoBarras(ean).get();
        Assertions.assertEquals(10, p.getEstoqueFiscal());
        Assertions.assertEquals(0, p.getEstoqueNaoFiscal());
    }

    @Test
    @DisplayName("Entrada de PF (Sem Nota): Deve registrar como estoque não fiscal")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeEntradaFornecedorPessoaFisica() {
        String ean = "7894444444444";
        criarProduto(ean, "33049990", "Produto Teste PF");

        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(ean);
        dto.setQuantidade(new BigDecimal("5"));
        dto.setPrecoCusto(new BigDecimal("15.00"));
        // Sem nota fiscal
        dto.setFormaPagamento(br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento.DINHEIRO); // Adicionado
        dto.setQuantidadeParcelas(1);

        estoqueService.registrarEntrada(dto);

        Produto p = produtoRepository.findByCodigoBarras(ean).get();
        Assertions.assertEquals(0, p.getEstoqueFiscal());
        Assertions.assertEquals(5, p.getEstoqueNaoFiscal());
    }

    private void criarProduto(String ean, String ncm, String desc) {
        Produto p = new Produto();
        p.setCodigoBarras(ean);
        p.setDescricao(desc);
        p.setNcm(ncm);
        p.setPrecoVenda(new BigDecimal("10.00"));
        p.setEstoqueFiscal(0);
        p.setEstoqueNaoFiscal(0);
        p.setQuantidadeEmEstoque(0);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setAtivo(true);

        calculadoraFiscalService.aplicarRegrasFiscais(p);

        produtoRepository.save(p);
    }
}