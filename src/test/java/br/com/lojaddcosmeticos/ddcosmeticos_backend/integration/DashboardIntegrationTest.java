package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.DashboardService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Dashboard CEO: Deve calcular vendas, financeiro e alertas corretamente")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeDashboardCompleto() {
        // --- 0. LIMPEZA (CRÍTICO: Remove dados inseridos pelo DataSeeder) ---
        // A ordem de deleção importa por causa das chaves estrangeiras
        contaPagarRepository.deleteAll();
        contaReceberRepository.deleteAll();
        vendaRepository.deleteAll();
        produtoRepository.deleteAll();
        fornecedorRepository.deleteAll();
        usuarioRepository.deleteAll();

        // --- 1. PREPARAÇÃO DO USUÁRIO ---
        Usuario gerente = new Usuario();
        gerente.setNome("Gerente Teste");
        gerente.setMatricula("gerente");
        gerente.setEmail("gerente@dd.com");
        gerente.setSenha(passwordEncoder.encode("123456"));
        gerente.setPerfilDoUsuario(PerfilDoUsuario.ROLE_ADMIN);
        gerente.setAtivo(true);
        usuarioRepository.save(gerente);

        // --- CENÁRIO ---
        LocalDate hoje = LocalDate.now();

        // 2. Produtos (Códigos Numéricos)
        criarProduto("789001001", new BigDecimal("100.00"), 10, 5); // PROD_OK (10 > 5)
        criarProduto("789001002", new BigDecimal("50.00"), 2, 5);   // PROD_BAIXO (2 < 5) -> Conta 1 aqui

        // 3. Vendas
        criarVenda(hoje.atTime(10, 0), new BigDecimal("100.00"), gerente);
        criarVenda(hoje.atTime(15, 30), new BigDecimal("200.00"), gerente);
        criarVenda(hoje.minusDays(1).atTime(12, 0), new BigDecimal("5000.00"), gerente);

        // 4. Financeiro
        Fornecedor f = criarFornecedor();
        criarContaPagar(f, new BigDecimal("50.00"), hoje.minusDays(1), StatusConta.PENDENTE); // Vencido
        criarContaPagar(f, new BigDecimal("120.00"), hoje, StatusConta.PENDENTE);             // Pagar Hoje
        criarContaPagar(f, new BigDecimal("1000.00"), hoje.plusDays(1), StatusConta.PENDENTE);

        criarContaReceber(new BigDecimal("400.00"), hoje);      // Receber Hoje
        criarContaReceber(new BigDecimal("900.00"), hoje.plusDays(1));

        // --- AÇÃO ---
        DashboardResumoDTO dashboard = dashboardService.obterResumoGeral();

        // --- VALIDAÇÕES ---
        // Vendas hoje: 2 (a de 5000 foi ontem)
        Assertions.assertEquals(2L, dashboard.getQuantidadeVendasHoje());

        // Total vendido hoje: 100 + 200 = 300
        Assertions.assertEquals(0, new BigDecimal("300.00").compareTo(dashboard.getTotalVendidoHoje()));

        // Saldo Dia: (ReceberHoje 400 + VendidoHoje 300) - PagarHoje 120 = 580
        BigDecimal saldoEsperado = new BigDecimal("400.00").add(new BigDecimal("300.00")).subtract(new BigDecimal("120.00"));
        Assertions.assertEquals(0, saldoEsperado.compareTo(dashboard.getSaldoDoDia()));

        // Total Vencido: 50
        Assertions.assertEquals(0, new BigDecimal("50.00").compareTo(dashboard.getTotalVencidoPagar()));

        // Produtos Abaixo Mínimo: 1 (apenas o PROD_BAIXO, pois limpamos o banco antes)
        Assertions.assertEquals(1L, dashboard.getProdutosAbaixoMinimo());
    }

    private void criarProduto(String codigo, BigDecimal preco, Integer estoque, Integer minimo) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("Teste " + codigo);
        p.setPrecoVenda(preco);
        p.setEstoqueFiscal(0);
        p.setEstoqueNaoFiscal(estoque);
        p.setQuantidadeEmEstoque(estoque);
        p.setEstoqueMinimo(minimo);
        p.setAtivo(true);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCusto(BigDecimal.ZERO);
        produtoRepository.save(p);
    }

    private void criarVenda(LocalDateTime data, BigDecimal total, Usuario usuario) {
        Venda v = new Venda();
        v.setDataVenda(data);
        v.setValorTotal(total);
        v.setFormaDePagamento(FormaDePagamento.DINHEIRO);
        v.setUsuario(usuario);
        v.setStatusNfce(StatusFiscal.PENDENTE);
        vendaRepository.save(v);
    }

    private Fornecedor criarFornecedor() {
        Fornecedor f = new Fornecedor();
        f.setRazaoSocial("Fornecedor Teste");
        f.setCnpj("00000000000100");
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
        contaPagarRepository.save(c);
    }

    private void criarContaReceber(BigDecimal valor, LocalDate vencimento) {
        ContaReceber c = new ContaReceber();
        c.setValorPago(valor);
        c.setValorTotal(valor);
        c.setDataVencimento(vencimento);
        c.setDataEmissao(LocalDate.now());
        c.setStatus(StatusConta.PENDENTE);
        contaReceberRepository.save(c);
    }
}