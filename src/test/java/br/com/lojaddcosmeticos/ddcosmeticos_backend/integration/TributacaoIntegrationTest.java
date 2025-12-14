package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.TributacaoService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@Transactional
// Esta linha faz a mágica: substitui qualquer banco configurado pelo H2 em memória
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test") // Garante isolamento
public class TributacaoIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private TributacaoService tributacaoService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;

    @Test
    @DisplayName("Deve identificar NCM de Shampoo e marcar como Monofásico automaticamente")
    public void testeClassificacaoShampoo() {
        Produto p = new Produto();
        p.setCodigoBarras("78910001000");
        p.setDescricao("SHAMPOO SEDA CERAMIDAS 325ML");
        p.setPrecoVenda(new BigDecimal("15.00"));
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setAtivo(true);
        produtoRepository.save(p);

        tributacaoService.classificarProduto(p);

        Assertions.assertEquals("33051000", p.getNcm(), "O NCM deveria ser de Shampoo");
        Assertions.assertTrue(p.isMonofasico(), "Shampoo deve ser Monofásico");
    }

    @Test
    @DisplayName("Deve identificar NCM de Sabonete")
    public void testeClassificacaoSabonete() {
        Produto p = new Produto();
        p.setDescricao("SABONETE DOVE ORIGINAL 90G");
        tributacaoService.classificarProduto(p);

        Assertions.assertTrue(p.getNcm().startsWith("3401"), "NCM deve ser de sabonete");
    }

    @Test
    @DisplayName("Fluxo End-to-End: Entrada com CPF deve gerar XML de Nota Avulsa")
    @WithMockUser(username = "gerente_teste", roles = {"GERENTE"})
    public void testeEntradaFornecedorPessoaFisica() {
        // 1. Preparação
        Produto produto = new Produto();
        produto.setCodigoBarras("TESTE_PF_01");
        produto.setDescricao("CONDICIONADOR PANTENE");
        produto.setPrecoVenda(BigDecimal.TEN);
        produto.setQuantidadeEmEstoque(BigDecimal.ZERO);
        produtoRepository.save(produto);

        // 2. Dados da Entrada
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras("TESTE_PF_01");
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(new BigDecimal("5.00"));
        dto.setFornecedorCnpj("123.456.789-00"); // CPF
        dto.setNumeroNotaFiscal("RECIBO_SIMPLES");

        // 3. Execução
        estoqueService.registrarEntrada(dto);

        // 4. Validações
        Produto produtoAtualizado = produtoRepository.findByCodigoBarras("TESTE_PF_01").get();
        Assertions.assertTrue(produtoAtualizado.isMonofasico()); // Deve ter classificado

        // Busca usando a regra limpa (sem pontos)
        Fornecedor fornecedor = fornecedorRepository.findByCpfOuCnpj("12345678900").orElseThrow();
        Assertions.assertEquals("FISICA", fornecedor.getTipoPessoa());

        List<Auditoria> logs = auditoriaRepository.findAll();
        boolean achouXml = logs.stream().anyMatch(log ->
                log.getMensagem().contains("NOTA DE ENTRADA (CPF) GERADA")
        );
        Assertions.assertTrue(achouXml);
    }

    @Test
    @DisplayName("Fluxo End-to-End: Entrada com CNPJ NÃO deve gerar XML")
    @WithMockUser(username = "gerente_teste", roles = {"GERENTE"})
    public void testeEntradaFornecedorJuridica() {
        Produto produto = new Produto();
        produto.setCodigoBarras("TESTE_PJ_01");
        produto.setDescricao("PERFUME IMPORTADO");
        produto.setPrecoVenda(new BigDecimal("200.00"));
        produto.setQuantidadeEmEstoque(BigDecimal.ZERO);
        produtoRepository.save(produto);

        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras("TESTE_PJ_01");
        dto.setQuantidade(new BigDecimal("5"));
        dto.setPrecoCusto(new BigDecimal("100.00"));
        dto.setFornecedorCnpj("12.345.678/0001-99"); // CNPJ
        dto.setNumeroNotaFiscal("NFE-5555");

        estoqueService.registrarEntrada(dto);

        List<Auditoria> logs = auditoriaRepository.findAll();
        boolean gerouXml = logs.stream().anyMatch(log ->
                log.getMensagem().contains("NOTA DE ENTRADA (CPF) GERADA")
        );

        Assertions.assertFalse(gerouXml, "Não deve gerar nota própria para CNPJ");
    }
}