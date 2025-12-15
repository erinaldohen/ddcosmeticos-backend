package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.FormaPagamento;
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
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ddcosmeticos_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class EstoqueFinanceiroIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;

    // ==================================================================================
    // GRUPO 1: PAGAMENTOS IMEDIATOS (PIX, DINHEIRO, DÉBITO)
    // Devem gerar conta com Status PAGO e Data de Pagamento preenchida.
    // ==================================================================================

    @Test
    @DisplayName("PIX: Deve gerar conta e BAIXAR automaticamente (PAGO)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraAvistaPix() {
        executarTestePagamentoImediato(FormaPagamento.PIX, "PROD_PIX", "NF-PIX");
    }

    @Test
    @DisplayName("DINHEIRO: Deve gerar conta e BAIXAR automaticamente (PAGO)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraAvistaDinheiro() {
        executarTestePagamentoImediato(FormaPagamento.DINHEIRO, "PROD_DIN", "NF-DIN");
    }

    @Test
    @DisplayName("DÉBITO: Deve gerar conta e BAIXAR automaticamente (PAGO)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraAvistaDebito() {
        executarTestePagamentoImediato(FormaPagamento.DEBITO, "PROD_DEB", "NF-DEB");
    }

    private void executarTestePagamentoImediato(FormaPagamento forma, String codBarras, String nf) {
        Produto p = criarProduto(codBarras, new BigDecimal("50.00"));

        EstoqueRequestDTO dto = criarDtoBase(codBarras, nf);
        dto.setFormaPagamento(forma);
        dto.setQuantidadeParcelas(1);

        estoqueService.registrarEntrada(dto);

        // Validação Genérica para Imediatos
        List<ContaPagar> contas = contaPagarRepository.findAll();
        // Filtra pela NF para garantir isolamento se rodar em paralelo (mesmo com Transactional)
        ContaPagar conta = contas.stream()
                .filter(c -> c.getDescricao().contains(nf))
                .findFirst()
                .orElseThrow();

        Assertions.assertEquals(ContaPagar.StatusConta.PAGO, conta.getStatus(),
                "Pagamento via " + forma + " deve ser PAGO imediatamente");
        Assertions.assertEquals(LocalDate.now(), conta.getDataPagamento(),
                "Data de pagamento deve ser hoje");
        Assertions.assertTrue(new BigDecimal("200.00").compareTo(conta.getValorTotal()) == 0);

        System.out.println(">>> SUCESSO: " + forma + " validado.");
    }

    // ==================================================================================
    // GRUPO 2: PAGAMENTOS FUTUROS (BOLETO, CRÉDITO)
    // Devem gerar conta com Status PENDENTE.
    // ==================================================================================

    @Test
    @DisplayName("BOLETO (A Prazo): Deve gerar conta PENDENTE para o futuro")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraBoleto() {
        criarProduto("PROD_BOLETO", new BigDecimal("100.00"));

        EstoqueRequestDTO dto = criarDtoBase("PROD_BOLETO", "NF-BOL");
        dto.setFormaPagamento(FormaPagamento.BOLETO);
        dto.setQuantidadeParcelas(1);

        // Simula vencimento para daqui a 10 dias
        LocalDate dataVencimento = LocalDate.now().plusDays(10);
        dto.setDataVencimentoBoleto(dataVencimento);

        estoqueService.registrarEntrada(dto);

        ContaPagar conta = contaPagarRepository.findAll().stream()
                .filter(c -> c.getDescricao().contains("NF-BOL"))
                .findFirst().orElseThrow();

        Assertions.assertEquals(ContaPagar.StatusConta.PENDENTE, conta.getStatus());
        Assertions.assertNull(conta.getDataPagamento(), "Não deve ter data de pagamento ainda");
        Assertions.assertEquals(dataVencimento, conta.getDataVencimento(), "Vencimento deve respeitar o input");

        System.out.println(">>> SUCESSO: BOLETO validado.");
    }

    @Test
    @DisplayName("CRÉDITO (Parcelado 3x): Deve gerar 3 contas PENDENTES")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraParceladaCartao() {
        criarProduto("PROD_CRED", new BigDecimal("300.00"));

        // Entrada: 3 un * R$ 100,00 = R$ 300,00 (Total) + 0 impostos no DTO base (simplificado)
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras("PROD_CRED");
        dto.setQuantidade(new BigDecimal("3"));
        dto.setPrecoCusto(new BigDecimal("100.00"));
        dto.setFornecedorCnpj("22.222.222/0001-22");
        dto.setNumeroNotaFiscal("NF-PARC");

        dto.setFormaPagamento(FormaPagamento.CREDITO);
        dto.setQuantidadeParcelas(3);

        estoqueService.registrarEntrada(dto);

        // Ordena por vencimento para validar a sequência (30, 60, 90 dias)
        List<ContaPagar> contas = contaPagarRepository.findByFornecedorId(
                contaPagarRepository.findAll().get(0).getFornecedor().getId()
        );
        // Filtra apenas as desse teste
        contas = contas.stream()
                .filter(c -> c.getDescricao().contains("NF-PARC"))
                .sorted((c1, c2) -> c1.getDataVencimento().compareTo(c2.getDataVencimento()))
                .toList();

        Assertions.assertEquals(3, contas.size(), "Devem ser geradas 3 parcelas");

        // Validações
        ContaPagar p1 = contas.get(0);
        Assertions.assertEquals(ContaPagar.StatusConta.PENDENTE, p1.getStatus());
        Assertions.assertTrue(new BigDecimal("100.00").compareTo(p1.getValorTotal()) == 0);
        Assertions.assertTrue(p1.getDescricao().contains("(Parc 1/3)"));

        ContaPagar p3 = contas.get(2);
        Assertions.assertEquals(LocalDate.now().plusDays(90), p3.getDataVencimento());
        Assertions.assertTrue(p3.getDescricao().contains("(Parc 3/3)"));

        System.out.println(">>> SUCESSO: CRÉDITO 3x validado.");
    }

    // --- Métodos Auxiliares para limpar o código ---

    private Produto criarProduto(String codigo, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("PRODUTO TESTE " + codigo);
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setPrecoVenda(precoVenda);
        return produtoRepository.save(p);
    }

    private EstoqueRequestDTO criarDtoBase(String codigo, String nf) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(codigo);
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(new BigDecimal("20.00")); // Total 200.00
        dto.setFornecedorCnpj("11.111.111/0001-11");
        dto.setNumeroNotaFiscal(nf);
        return dto;
    }
}