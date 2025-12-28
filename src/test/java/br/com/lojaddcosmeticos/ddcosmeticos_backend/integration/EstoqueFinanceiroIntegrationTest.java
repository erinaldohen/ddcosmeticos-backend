package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class EstoqueFinanceiroIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;

    @Test
    @DisplayName("PIX: Deve gerar conta e BAIXAR automaticamente (PAGO)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraAvistaPix() {
        executarTestePagamentoImediato(FormaDePagamento.PIX, "PROD_PIX", "NF-PIX");
    }

    @Test
    @DisplayName("DINHEIRO: Deve gerar conta e BAIXAR automaticamente (PAGO)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraAvistaDinheiro() {
        executarTestePagamentoImediato(FormaDePagamento.DINHEIRO, "PROD_DIN", "NF-DIN");
    }

    @Test
    @DisplayName("DÉBITO: Deve gerar conta e BAIXAR automaticamente (PAGO)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraAvistaDebito() {
        executarTestePagamentoImediato(FormaDePagamento.DEBITO, "PROD_DEB", "NF-DEB");
    }

    private void executarTestePagamentoImediato(FormaDePagamento forma, String codBarras, String nf) {
        criarProduto(codBarras, new BigDecimal("50.00"));

        EstoqueRequestDTO dto = criarDtoBase(codBarras, nf);
        dto.setFormaPagamento(forma);
        dto.setQuantidadeParcelas(1);

        estoqueService.registrarEntrada(dto);

        List<ContaPagar> contas = contaPagarRepository.findAll();
        ContaPagar conta = contas.stream()
                .filter(c -> c.getDescricao() != null && c.getDescricao().contains(nf))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Conta não encontrada para NF " + nf));

        Assertions.assertEquals(StatusConta.PAGO, conta.getStatus());
        Assertions.assertEquals(LocalDate.now(), conta.getDataPagamento());
        Assertions.assertTrue(new BigDecimal("200.00").compareTo(conta.getValorTotal()) == 0);
    }

    @Test
    @DisplayName("BOLETO (A Prazo): Deve gerar conta PENDENTE para o futuro")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraBoleto() {
        criarProduto("PROD_BOLETO", new BigDecimal("100.00"));

        EstoqueRequestDTO dto = criarDtoBase("PROD_BOLETO", "NF-BOL");
        dto.setFormaPagamento(FormaDePagamento.BOLETO);
        dto.setQuantidadeParcelas(1);
        dto.setDataVencimentoBoleto(LocalDate.now().plusDays(10));

        estoqueService.registrarEntrada(dto);

        ContaPagar conta = contaPagarRepository.findAll().stream()
                .filter(c -> c.getDescricao() != null && c.getDescricao().contains("NF-BOL"))
                .findFirst().orElseThrow();

        Assertions.assertEquals(StatusConta.PENDENTE, conta.getStatus());
        Assertions.assertNull(conta.getDataPagamento());
    }

    @Test
    @DisplayName("CRÉDITO (Parcelado 3x): Deve gerar 3 contas PENDENTES")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraParceladaCartao() {
        criarProduto("PROD_CRED", new BigDecimal("300.00"));

        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras("PROD_CRED");
        dto.setQuantidade(new BigDecimal("3"));
        dto.setPrecoCusto(new BigDecimal("100.00"));
        dto.setFornecedorCnpj("22222222000122"); // CNPJ Limpo
        dto.setNumeroNotaFiscal("NF-PARC");
        dto.setFormaPagamento(FormaDePagamento.CREDITO);
        dto.setQuantidadeParcelas(3);

        estoqueService.registrarEntrada(dto);

        List<ContaPagar> contas = contaPagarRepository.findAll().stream()
                .filter(c -> c.getDescricao() != null && c.getDescricao().contains("NF-PARC"))
                .sorted(Comparator.comparing(ContaPagar::getDataVencimento))
                .collect(Collectors.toList());

        Assertions.assertEquals(3, contas.size());
    }

    private Produto criarProduto(String codigo, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("PRODUTO TESTE " + codigo);
        p.setQuantidadeEmEstoque(0);
        p.setPrecoVenda(precoVenda);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setAtivo(true);
        p.setPossuiNfEntrada(true);
        return produtoRepository.save(p);
    }

    private EstoqueRequestDTO criarDtoBase(String codigo, String nf) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(codigo);
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(new BigDecimal("20.00"));
        dto.setFornecedorCnpj("11111111000111"); // CNPJ Limpo
        dto.setNumeroNotaFiscal(nf);
        return dto;
    }
}