package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
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
import java.util.List;

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class EstoqueIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;

    @Test
    @DisplayName("ENTRADA: Deve atualizar estoque, calcular custo médio e gerar financeiro")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeEntradaComFinanceiro() {
        String codigoBarras = "789100010001";
        criarProduto(codigoBarras, new BigDecimal("100.00"), 0);

        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(codigoBarras);
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(new BigDecimal("50.00"));
        dto.setNumeroNotaFiscal("NF-1234");
        dto.setFornecedorCnpj("00000000000100"); // CNPJ LIMPO
        dto.setFormaPagamento(FormaDePagamento.BOLETO);
        dto.setQuantidadeParcelas(1);

        estoqueService.registrarEntrada(dto);

        Produto produtoAtualizado = produtoRepository.findByCodigoBarras(codigoBarras).orElseThrow();
        Assertions.assertEquals(10, produtoAtualizado.getQuantidadeEmEstoque());
        Assertions.assertTrue(new BigDecimal("50.0000").compareTo(produtoAtualizado.getPrecoMedioPonderado()) == 0);

        List<ContaPagar> contas = contaPagarRepository.findAll();
        Assertions.assertFalse(contas.isEmpty());
    }

    @Test
    @DisplayName("AJUSTE: Deve reduzir estoque em caso de PERDA/QUEBRA")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeAjusteInventarioSaida() {
        String codigoBarras = "789100020002";
        Produto p = criarProduto(codigoBarras, new BigDecimal("50.00"), 20);
        p.setPrecoMedioPonderado(new BigDecimal("25.0000"));
        produtoRepository.save(p);

        AjusteEstoqueDTO dto = new AjusteEstoqueDTO();
        dto.setCodigoBarras(codigoBarras);
        dto.setQuantidade(new BigDecimal("2"));
        dto.setMotivo(MotivoMovimentacaoDeEstoque.AJUSTE_PERDA.name());

        estoqueService.realizarAjusteInventario(dto);

        Produto produtoAtualizado = produtoRepository.findByCodigoBarras(codigoBarras).orElseThrow();
        Assertions.assertEquals(18, produtoAtualizado.getQuantidadeEmEstoque());
    }

    private Produto criarProduto(String codigo, BigDecimal precoVenda, Integer estoqueInicial) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("PRODUTO TESTE " + codigo);
        p.setQuantidadeEmEstoque(estoqueInicial);
        p.setPrecoVenda(precoVenda);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setAtivo(true);
        p.isPossuiNfEntrada();
        return produtoRepository.save(p);
    }
}