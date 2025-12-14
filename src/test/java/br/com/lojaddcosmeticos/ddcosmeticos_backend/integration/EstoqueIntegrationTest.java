package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY) // Usa H2 em memória
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ddcosmeticos_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class EstoqueIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;

    @Test
    @DisplayName("INTEGRAÇÃO TOTAL: Entrada de Estoque deve gerar Conta a Pagar e calcular Custo Real")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeEntradaComFinanceiro() {
        // 1. Preparação: Produto com estoque zero
        Produto p = new Produto();
        p.setCodigoBarras("PROD_FINANCEIRO_01");
        p.setDescricao("BATOM MATTE");
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setPrecoVenda(new BigDecimal("30.00"));
        produtoRepository.save(p);

        // 2. Dados da Entrada (Simulando o Front)
        // Compra: 10 unidades a R$ 10,00 cada.
        // Impostos extras (Frete/ST): R$ 20,00.
        // Total esperado da conta: R$ 120,00.
        // Custo Unitário Real esperado: (100 + 20) / 10 = R$ 12,00.

        EstoqueRequestDTO entrada = new EstoqueRequestDTO();
        entrada.setCodigoBarras("PROD_FINANCEIRO_01");
        entrada.setQuantidade(new BigDecimal("10"));
        entrada.setPrecoCusto(new BigDecimal("10.00"));
        entrada.setValorImpostosAdicionais(new BigDecimal("20.00")); // O pulo do gato
        entrada.setFornecedorCnpj("99.999.999/0001-99");
        entrada.setNumeroNotaFiscal("NF-100");
        entrada.setDataVencimentoBoleto(LocalDate.now().plusDays(15)); // Vence em 15 dias

        // 3. Execução
        estoqueService.registrarEntrada(entrada);

        // 4. Validações

        // A. Validação de Estoque e Custo (PMP)
        Produto produtoAtualizado = produtoRepository.findByCodigoBarras("PROD_FINANCEIRO_01").get();

        // CORREÇÃO: Usar compareTo para ignorar diferenças de escala (10.00 vs 10.000)
        Assertions.assertTrue(new BigDecimal("10.000").compareTo(produtoAtualizado.getQuantidadeEmEstoque()) == 0,
                "Estoque deveria ser 10 unidades");

        // Verifica se o sistema calculou o Custo Real (12.00) e não o da nota (10.00)
        Assertions.assertTrue(new BigDecimal("12.00").compareTo(produtoAtualizado.getPrecoMedioPonderado()) == 0,
                "O PMP deveria considerar os impostos adicionais (10 + 2 = 12.00)");

        // B. Validação Financeira (Conta a Pagar)
        List<ContaPagar> contas = contaPagarRepository.findAll();
        Assertions.assertFalse(contas.isEmpty(), "Deveria ter gerado uma conta a pagar");

        ContaPagar conta = contas.get(0);
        Assertions.assertTrue(new BigDecimal("120.00").compareTo(conta.getValorTotal()) == 0,
                "O valor da conta deve ser Produtos + Impostos (100 + 20 = 120)");

        Assertions.assertEquals("PENDENTE", conta.getStatus().name());

        // Verifica se limpou a formatação do CNPJ corretamente
        Assertions.assertEquals("99999999000199", conta.getFornecedor().getCpfOuCnpj());

        System.out.println(">>> SUCESSO: Estoque atualizado para 10un e Dívida de R$ 120,00 gerada!");
    }
}