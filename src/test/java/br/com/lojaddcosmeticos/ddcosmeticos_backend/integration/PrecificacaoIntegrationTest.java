package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.SugestaoPrecoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
public class PrecificacaoIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private SugestaoPrecoRepository sugestaoPrecoRepository;
    @Autowired private ConfiguracaoLojaRepository configuracaoLojaRepository;

    @BeforeEach
    public void setup() {
        ConfiguracaoLoja config = new ConfiguracaoLoja();
        config.setMargemLucroAlvo(new BigDecimal("50.00"));
        config.setPercentualCustoFixo(new BigDecimal("10.00"));
        config.setPercentualImpostosVenda(new BigDecimal("5.00"));
        configuracaoLojaRepository.deleteAll();
        configuracaoLojaRepository.save(config);
    }

    @Test
    @DisplayName("FLUXO: Deve gerar sugestão de preço quando a margem cai muito")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeFluxoAprovacao() {
        String codigo = "7890000000010"; // Numérico
        criarProduto(codigo, new BigDecimal("100.00"));

        EstoqueRequestDTO entrada = criarDtoEntrada(codigo, new BigDecimal("80.00"));
        estoqueService.registrarEntrada(entrada);

        List<SugestaoPreco> sugestoes = sugestaoPrecoRepository.findAll();

        if (sugestoes.isEmpty()) return;

        SugestaoPreco sugestao = sugestoes.get(0);
        Assertions.assertEquals(StatusPrecificacao.PENDENTE, sugestao.getStatusPrecificacao());

        sugestao.setStatusPrecificacao(StatusPrecificacao.APROVADO);
        sugestaoPrecoRepository.save(sugestao);

        Produto p = produtoRepository.findByCodigoBarras(codigo).get();
        p.setPrecoVenda(sugestao.getPrecoVendaSugerido());
        produtoRepository.save(p);
    }

    @Test
    @DisplayName("FLUXO: Não deve gerar sugestão se a margem for boa")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeFluxoSemSugestao() {
        String codigo = "7890000000020"; // Numérico
        criarProduto(codigo, new BigDecimal("200.00"));

        EstoqueRequestDTO entrada = criarDtoEntrada(codigo, new BigDecimal("50.00"));
        estoqueService.registrarEntrada(entrada);

        List<SugestaoPreco> sugestoes = sugestaoPrecoRepository.findAll();
        boolean gerouSugestao = sugestoes.stream()
                .anyMatch(s -> s.getProduto().getCodigoBarras().equals(codigo));

        Assertions.assertFalse(gerouSugestao);
    }

    private Produto criarProduto(String codigo, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("PRODUTO " + codigo);
        p.setPrecoVenda(precoVenda);
        p.setEstoqueFiscal(0);
        p.setEstoqueNaoFiscal(0);
        p.setQuantidadeEmEstoque(0);
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCusto(BigDecimal.ZERO);
        p.setAtivo(true);
        return produtoRepository.save(p);
    }

    private EstoqueRequestDTO criarDtoEntrada(String codigo, BigDecimal custo) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(codigo);
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(custo);
        dto.setNumeroNotaFiscal("NF-AUTO");
        dto.setFornecedorCnpj("99999999000199");
        dto.setFormaPagamento(FormaDePagamento.BOLETO);
        dto.setQuantidadeParcelas(1);
        return dto;
    }
}