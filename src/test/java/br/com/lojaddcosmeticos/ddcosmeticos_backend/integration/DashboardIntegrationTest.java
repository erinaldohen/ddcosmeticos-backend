package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DashboardService;
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
import java.time.LocalDateTime;

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class DashboardIntegrationTest {

    @Autowired private DashboardService dashboardService;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FornecedorRepository fornecedorRepository;

    @Test
    @DisplayName("Dashboard CEO: Deve calcular vendas, financeiro e alertas corretamente")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeDashboardCompleto() {
        // --- CENÁRIO (PREPARAÇÃO DOS DADOS) ---
        LocalDate hoje = LocalDate.now();

        // 1. Produtos (1 Normal, 1 Baixo Estoque)
        criarProduto("PROD_OK", new BigDecimal("100.00"), new BigDecimal("10"), new BigDecimal("5")); // Estoque 10 > Min 5
        criarProduto("PROD_BAIXO", new BigDecimal("50.00"), new BigDecimal("2"), new BigDecimal("5"));  // Estoque 2 < Min 5 (ALERTA!)

        // 2. Vendas (2 Hoje, 1 Ontem)
        criarVenda(hoje.atTime(10, 0), new BigDecimal("100.00")); // Venda 1 Hoje
        criarVenda(hoje.atTime(15, 30), new BigDecimal("200.00")); // Venda 2 Hoje
        criarVenda(hoje.minusDays(1).atTime(12, 0), new BigDecimal("5000.00")); // Ontem (NÃO DEVE CONTAR)

        // 3. Financeiro - Contas a Pagar
        Fornecedor f = criarFornecedor();
        // Atrasada (Venceu ontem) -> Alerta
        criarContaPagar(f, new BigDecimal("50.00"), hoje.minusDays(1), StatusConta.PENDENTE);
        // Vence Hoje -> Fluxo do Dia
        criarContaPagar(f, new BigDecimal("120.00"), hoje, StatusConta.PENDENTE);
        // Vence Amanhã -> Projeção Futura
        criarContaPagar(f, new BigDecimal("1000.00"), hoje.plusDays(1), StatusConta.PENDENTE);

        // 4. Financeiro - Contas a Receber
        // Recebe Hoje -> Fluxo do Dia
        criarContaReceber(new BigDecimal("400.00"), hoje);
        // Recebe Amanhã
        criarContaReceber(new BigDecimal("900.00"), hoje.plusDays(1));


        // --- AÇÃO (CHAMA O DASHBOARD) ---
        DashboardResumoDTO dashboard = dashboardService.obterResumoExecutivo();


        // --- VALIDAÇÕES (O QUE O CEO VÊ) ---

        // 1. Vendas de HOJE
        Assertions.assertEquals(2L, dashboard.getQuantidadeVendasHoje(), "Devem ser 2 vendas hoje");
        Assertions.assertTrue(new BigDecimal("300.00").compareTo(dashboard.getTotalVendidoHoje()) == 0,
                "Total vendido deve ser 100 + 200 = 300");
        Assertions.assertTrue(new BigDecimal("150.00").compareTo(dashboard.getTicketMedioHoje()) == 0,
                "Ticket médio deve ser 300 / 2 = 150");

        // 2. Fluxo Financeiro de HOJE
        Assertions.assertTrue(new BigDecimal("120.00").compareTo(dashboard.getAPagarHoje()) == 0,
                "A pagar hoje deve ser 120");
        Assertions.assertTrue(new BigDecimal("400.00").compareTo(dashboard.getAReceberHoje()) == 0,
                "A receber hoje deve ser 400");

        // Saldo do Dia (400 - 120 = 280)
        BigDecimal saldoEsperado = new BigDecimal("400.00").subtract(new BigDecimal("120.00"));
        Assertions.assertTrue(saldoEsperado.compareTo(dashboard.getSaldoDoDia()) == 0);

        // 3. Alertas (Problemas)
        Assertions.assertTrue(new BigDecimal("50.00").compareTo(dashboard.getTotalVencidoPagar()) == 0,
                "Deve alertar 50 reais de contas atrasadas");
        Assertions.assertEquals(1L, dashboard.getProdutosAbaixoMinimo(),
                "Deve alertar 1 produto com estoque baixo");

        // 4. Projeção de Amanhã (Primeiro item da lista de projeção futura, ou índice 1 se incluir hoje)
        // No service fizemos um loop de 7 dias começando por hoje (i=0) ou amanhã?
        // Se o loop começa em i=0 (hoje), o índice 1 é amanhã.
        var projecaoAmanha = dashboard.getProjecaoSemanal().get(1);

        Assertions.assertEquals(hoje.plusDays(1), projecaoAmanha.getData());
        Assertions.assertTrue(new BigDecimal("900.00").compareTo(projecaoAmanha.getAReceber()) == 0);
        Assertions.assertTrue(new BigDecimal("1000.00").compareTo(projecaoAmanha.getAPagar()) == 0);
        // Saldo amanhã deve ser negativo (-100)
        Assertions.assertTrue(new BigDecimal("-100.00").compareTo(projecaoAmanha.getSaldoPrevisto()) == 0);

        System.out.println(">>> SUCESSO: O Painel do CEO está exibindo os dados corretos!");
    }

    // --- MÉTODOS AUXILIARES ---

    private void criarProduto(String codigo, BigDecimal preco, BigDecimal estoque, BigDecimal minimo) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("Teste " + codigo);
        p.setPrecoVenda(preco);
        p.setQuantidadeEmEstoque(estoque);
        p.setEstoqueMinimo(minimo);
        p.setAtivo(true);
        p.setPossuiNfEntrada(true);
        produtoRepository.save(p);
    }

    private void criarVenda(LocalDateTime data, BigDecimal total) {
        Venda v = new Venda();
        v.setDataVenda(data);
        v.setTotalVenda(total);
        v.setFormaPagamento(FormaPagamento.DINHEIRO);
        vendaRepository.save(v);
    }

    private Fornecedor criarFornecedor() {
        Fornecedor f = new Fornecedor();
        f.setRazaoSocial("Fornecedor Teste");
        f.setCpfOuCnpj("00000000000100");
        f.setTipoPessoa("JURIDICA");
        f.setAtivo(true);
        return fornecedorRepository.save(f);
    }

    private void criarContaPagar(Fornecedor f, BigDecimal valor, LocalDate vencimento, StatusConta status) {
        ContaPagar c = new ContaPagar();
        c.setFornecedor(f);
        c.setValorTotal(valor);
        c.setDataVencimento(vencimento);
        c.setDataEmissao(LocalDate.now().minusDays(5));
        c.setStatus(status);
        c.setDescricao("Conta Teste");
        contaPagarRepository.save(c);
    }

    private void criarContaReceber(BigDecimal valor, LocalDate vencimento) {
        ContaReceber c = new ContaReceber();
        c.setValorLiquido(valor); // O dashboard soma pelo liquido
        c.setValorTotal(valor);
        c.setDataVencimento(vencimento);
        c.setDataEmissao(LocalDate.now());
        c.setStatus(ContaReceber.StatusConta.PENDENTE);
        c.setDescricao("Recebimento Teste");
        contaReceberRepository.save(c);
    }
}