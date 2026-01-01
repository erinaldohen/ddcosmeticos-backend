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

    @Test
    @DisplayName("Dashboard CEO: Deve calcular vendas, financeiro e alertas corretamente")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeDashboardCompleto() {
        // --- 0. PREPARAÇÃO DO USUÁRIO (Obrigatório para Venda) ---
        Usuario gerente = new Usuario();
        // CORREÇÃO: Usando setMatricula em vez de setLogin
        gerente.setEmail("gerente");
        gerente.setSenha("123");
        gerente.setPerfilDoUsuario(PerfilDoUsuario.ADMIN); // Ajustado para Enum correto se for ROLE_GERENTE ou GERENTE
        gerente.setNome("Gerente Teste");
        usuarioRepository.save(gerente);

        // --- CENÁRIO ---
        LocalDate hoje = LocalDate.now();

        // 1. Produtos
        criarProduto("PROD_OK", new BigDecimal("100.00"), 10, 5);
        criarProduto("PROD_BAIXO", new BigDecimal("50.00"), 2, 5);

        // 2. Vendas (Vinculando Usuário)
        criarVenda(hoje.atTime(10, 0), new BigDecimal("100.00"), gerente);
        criarVenda(hoje.atTime(15, 30), new BigDecimal("200.00"), gerente);
        criarVenda(hoje.minusDays(1).atTime(12, 0), new BigDecimal("5000.00"), gerente); // Ontem

        // 3. Financeiro
        Fornecedor f = criarFornecedor();
        criarContaPagar(f, new BigDecimal("50.00"), hoje.minusDays(1), StatusConta.PENDENTE);
        criarContaPagar(f, new BigDecimal("120.00"), hoje, StatusConta.PENDENTE);
        criarContaPagar(f, new BigDecimal("1000.00"), hoje.plusDays(1), StatusConta.PENDENTE);

        criarContaReceber(new BigDecimal("400.00"), hoje);
        criarContaReceber(new BigDecimal("900.00"), hoje.plusDays(1));

        // --- AÇÃO ---
        DashboardResumoDTO dashboard = dashboardService.obterResumo();

        // --- VALIDAÇÕES ---
        Assertions.assertEquals(2L, dashboard.quantidadeVendasHoje());
        Assertions.assertTrue(new BigDecimal("300.00").compareTo(dashboard.totalVendidoHoje()) == 0);

        BigDecimal saldoEsperado = new BigDecimal("400.00").add(new BigDecimal("300.00")).subtract(new BigDecimal("120.00"));
        Assertions.assertTrue(saldoEsperado.compareTo(dashboard.saldoDoDia()) == 0);

        Assertions.assertTrue(new BigDecimal("50.00").compareTo(dashboard.totalVencidoPagar()) == 0);
        Assertions.assertEquals(1L, dashboard.produtosAbaixoMinimo());
    }

    // --- MÉTODOS AUXILIARES ---

    private void criarProduto(String codigo, BigDecimal preco, Integer estoque, Integer minimo) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("Teste " + codigo);
        p.setPrecoVenda(preco);
        p.setQuantidadeEmEstoque(estoque);
        p.setEstoqueMinimo(minimo);
        p.setAtivo(true);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setPossuiNfEntrada(true);
        produtoRepository.save(p);
    }

    private void criarVenda(LocalDateTime data, BigDecimal total, Usuario usuario) {
        Venda v = new Venda();
        v.setDataVenda(data);
        v.setTotalVenda(total);
        v.setFormaPagamento(FormaDePagamento.DINHEIRO);
        v.setUsuario(usuario);
        v.setStatusFiscal(StatusFiscal.NAO_EMITIDA);
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
        contaPagarRepository.save(c);
    }

    private void criarContaReceber(BigDecimal valor, LocalDate vencimento) {
        ContaReceber c = new ContaReceber();
        c.setValorLiquido(valor);
        c.setValorTotal(valor);
        c.setDataVencimento(vencimento);
        c.setDataEmissao(LocalDate.now());
        c.setStatus(StatusConta.PENDENTE);
        contaReceberRepository.save(c);
    }
}