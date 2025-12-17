package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
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
        criarProduto(codBarras, new BigDecimal("50.00"));

        EstoqueRequestDTO dto = criarDtoBase(codBarras, nf);
        dto.setFormaPagamento(forma);
        dto.setQuantidadeParcelas(1);

        estoqueService.registrarEntrada(dto);

        List<ContaPagar> contas = contaPagarRepository.findAll();
        ContaPagar conta = contas.stream()
                .filter(c -> c.getDescricao().contains(nf))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Conta não encontrada para NF " + nf));

        Assertions.assertEquals(StatusConta.PAGO, conta.getStatus(),
                "Pagamento via " + forma + " deve ser PAGO imediatamente");
        Assertions.assertEquals(LocalDate.now(), conta.getDataPagamento(),
                "Data de pagamento deve ser hoje");
        Assertions.assertTrue(new BigDecimal("200.00").compareTo(conta.getValorTotal()) == 0);

        System.out.println(">>> SUCESSO: " + forma + " validado.");
    }

    @Test
    @DisplayName("BOLETO (A Prazo): Deve gerar conta PENDENTE para o futuro")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeCompraBoleto() {
        criarProduto("PROD_BOLETO", new BigDecimal("100.00"));

        EstoqueRequestDTO dto = criarDtoBase("PROD_BOLETO", "NF-BOL");
        dto.setFormaPagamento(FormaPagamento.BOLETO);
        dto.setQuantidadeParcelas(1);

        LocalDate dataVencimento = LocalDate.now().plusDays(10);
        dto.setDataVencimentoBoleto(dataVencimento);

        estoqueService.registrarEntrada(dto);

        ContaPagar conta = contaPagarRepository.findAll().stream()
                .filter(c -> c.getDescricao().contains("NF-BOL"))
                .findFirst().orElseThrow();

        Assertions.assertEquals(StatusConta.PENDENTE, conta.getStatus());
        Assertions.assertNull(conta.getDataPagamento(), "Não deve ter data de pagamento ainda");
        Assertions.assertEquals(dataVencimento, conta.getDataVencimento(), "Vencimento deve respeitar o input");

        System.out.println(">>> SUCESSO: BOLETO validado.");
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
        dto.setFornecedorCnpj("22.222.222/0001-22");
        dto.setNumeroNotaFiscal("NF-PARC");

        dto.setFormaPagamento(FormaPagamento.CREDITO);
        dto.setQuantidadeParcelas(3);

        estoqueService.registrarEntrada(dto);

        List<ContaPagar> contas = contaPagarRepository.findByFornecedorId(
                contaPagarRepository.findAll().get(0).getFornecedor().getId()
        );

        contas = contas.stream()
                .filter(c -> c.getDescricao().contains("NF-PARC"))
                .sorted(Comparator.comparing(ContaPagar::getDataVencimento))
                .collect(Collectors.toList());

        Assertions.assertEquals(3, contas.size(), "Devem ser geradas 3 parcelas");

        // Valida Parcela 1
        ContaPagar p1 = contas.get(0);
        Assertions.assertEquals(StatusConta.PENDENTE, p1.getStatus());
        Assertions.assertTrue(new BigDecimal("100.00").compareTo(p1.getValorTotal()) == 0);
        Assertions.assertTrue(p1.getDescricao().contains("(Parc 1/3)"));

        // Valida Parcela 3 (Vencimento em 90 dias)
        ContaPagar p3 = contas.get(2);
        Assertions.assertEquals(LocalDate.now().plusDays(90), p3.getDataVencimento());
        Assertions.assertTrue(p3.getDescricao().contains("(Parc 3/3)"));

        System.out.println(">>> SUCESSO: CRÉDITO 3x validado.");
    }

    private Produto criarProduto(String codigo, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("PRODUTO TESTE " + codigo);

        // Se o seu Produto usa 'quantidadeEmEstoque', mude para 'setQuantidadeEmEstoque'
        // Mas recomendo padronizar tudo para 'setQuantidadeEstoque'.
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);

        p.setPrecoVenda(precoVenda);

        // --- FIX CRUCIAL PARA O NULLPOINTEREXCEPTION ---
        // O erro acontecia porque esses valores estavam NULL no banco H2
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCustoInicial(BigDecimal.ZERO);
        // -----------------------------------------------

        p.setAtivo(true);
        p.setPossuiNfEntrada(true);
        return produtoRepository.save(p);
    }

    private EstoqueRequestDTO criarDtoBase(String codigo, String nf) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(codigo);
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(new BigDecimal("20.00"));
        dto.setFornecedorCnpj("11.111.111/0001-11");
        dto.setNumeroNotaFiscal(nf);
        return dto;
    }
}