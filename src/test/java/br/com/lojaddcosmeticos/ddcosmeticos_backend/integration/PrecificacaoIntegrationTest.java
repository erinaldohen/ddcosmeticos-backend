package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaPagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusSugestao;
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
        // Define uma meta de lucro agressiva (50%) para forçar a geração de sugestões
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
        // 1. Produto Vendido a R$ 100,00
        String codigo = "789_PRECO_TESTE";
        criarProduto(codigo, new BigDecimal("100.00"));

        // 2. Compra CARA (Custo R$ 80,00) -> Lucro Bruto R$ 20,00 (20%)
        // Como a meta é 50%, deve gerar alerta.
        EstoqueRequestDTO entrada = criarDtoEntrada(codigo, new BigDecimal("80.00"));
        estoqueService.registrarEntrada(entrada);

        // 3. Valida se gerou Sugestão
        List<SugestaoPreco> sugestoes = sugestaoPrecoRepository.findAll();
        Assertions.assertFalse(sugestoes.isEmpty(), "Deveria ter gerado uma sugestão de preço");

        SugestaoPreco sugestao = sugestoes.get(0);
        Assertions.assertEquals(StatusSugestao.PENDENTE, sugestao.getStatus());
        Assertions.assertTrue(sugestao.getMotivo().contains("abaixo da meta"));

        // Simula APROVAÇÃO (Aqui você usaria seu Service de aprovação se tivesse)
        sugestao.setStatus(StatusSugestao.APROVADO);
        sugestaoPrecoRepository.save(sugestao);

        // Aplica o preço sugerido no produto
        Produto p = produtoRepository.findByCodigoBarras(codigo).get();
        p.setPrecoVenda(sugestao.getPrecoVendaSugerido());
        produtoRepository.save(p);

        System.out.println(">>> SUCESSO: Sugestão gerada e aprovada.");
    }

    @Test
    @DisplayName("FLUXO: Não deve gerar sugestão se a margem for boa")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeFluxoSemSugestao() {
        String codigo = "789_LUCRO_BOM";
        criarProduto(codigo, new BigDecimal("200.00"));

        // Compra Barata (Custo R$ 50,00) -> Lucro R$ 150 (75%)
        // Meta é 50%, então NÃO deve gerar sugestão
        EstoqueRequestDTO entrada = criarDtoEntrada(codigo, new BigDecimal("50.00"));
        estoqueService.registrarEntrada(entrada);

        List<SugestaoPreco> sugestoes = sugestaoPrecoRepository.findAll();
        // Filtra apenas para esse produto caso o banco não esteja limpo
        boolean gerouSugestao = sugestoes.stream()
                .anyMatch(s -> s.getProduto().getCodigoBarras().equals(codigo));

        Assertions.assertFalse(gerouSugestao, "Não deveria gerar sugestão pois a margem está ótima");
    }

    @Test
    @DisplayName("FLUXO: Rejeição de Sugestão (Mantém preço antigo)")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeFluxoRejeicao() {
        String codigo = "789_REJEICAO";
        criarProduto(codigo, new BigDecimal("100.00"));

        // Força sugestão
        EstoqueRequestDTO entrada = criarDtoEntrada(codigo, new BigDecimal("85.00"));
        estoqueService.registrarEntrada(entrada);

        SugestaoPreco sugestao = sugestaoPrecoRepository.findAll().stream()
                .filter(s -> s.getProduto().getCodigoBarras().equals(codigo))
                .findFirst().orElseThrow();

        // Simula REJEIÇÃO
        sugestao.setStatus(StatusSugestao.REJEITADO);
        sugestaoPrecoRepository.save(sugestao);

        // Verifica que o preço do produto NÃO mudou
        Produto p = produtoRepository.findByCodigoBarras(codigo).get();
        Assertions.assertEquals(0, new BigDecimal("100.00").compareTo(p.getPrecoVenda()), "Preço não deve mudar ao rejeitar");
    }

    // --- MÉTODOS AUXILIARES ---

    private Produto criarProduto(String codigo, BigDecimal precoVenda) {
        Produto p = new Produto();
        p.setCodigoBarras(codigo);
        p.setDescricao("PRODUTO " + codigo);
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setPrecoVenda(precoVenda);

        // --- AQUI ESTAVA O ERRO (FIX CORRIGIDO) ---
        p.setPrecoMedioPonderado(BigDecimal.ZERO);
        p.setPrecoCustoInicial(BigDecimal.ZERO);
        // ------------------------------------------

        p.setAtivo(true);
        p.setPossuiNfEntrada(true);
        return produtoRepository.save(p);
    }

    private EstoqueRequestDTO criarDtoEntrada(String codigo, BigDecimal custo) {
        EstoqueRequestDTO dto = new EstoqueRequestDTO();
        dto.setCodigoBarras(codigo);
        dto.setQuantidade(new BigDecimal("10"));
        dto.setPrecoCusto(custo);
        dto.setNumeroNotaFiscal("NF-AUTO");
        dto.setFornecedorCnpj("99.999.999/0001-99");
        dto.setFormaPagamento(FormaPagamento.BOLETO);
        dto.setQuantidadeParcelas(1);
        return dto;
    }
}