package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PrecificacaoService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ddcosmeticos_precificacao_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "api.security.token.secret=TesteSecretKey12345678901234567890"
})
public class PrecificacaoIntegrationTest {

    @Autowired private EstoqueService estoqueService;
    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private SugestaoPrecoRepository sugestaoRepository;
    @Autowired private ConfiguracaoLojaRepository configRepository;
    @Autowired private FornecedorRepository fornecedorRepository;

    private Produto produtoTeste;

    @BeforeEach
    public void setup() {
        // 1. Configura a Loja (Regras do Jogo)
        // Impostos (10%) + Fixo (20%) + Lucro (20%) = 50% de Custo Total
        // Divisor = 0.5
        ConfiguracaoLoja config = new ConfiguracaoLoja();
        config.setPercentualImpostosVenda(new BigDecimal("10"));
        config.setPercentualCustoFixo(new BigDecimal("20"));
        config.setMargemLucroAlvo(new BigDecimal("20"));
        configRepository.save(config);

        // 2. Cria Produto Inicial
        // Preço Venda: R$ 100,00 | Custo Inicial: R$ 40,00
        Produto p = new Produto();
        p.setCodigoBarras("PROD_INFLACAO");
        p.setDescricao("SHAMPOO PREMIUM");
        p.setPrecoVenda(new BigDecimal("100.00"));
        p.setPrecoCustoInicial(new BigDecimal("40.00"));
        p.setQuantidadeEmEstoque(BigDecimal.ZERO);
        p.setAtivo(true);
        p.setPossuiNfEntrada(true);
        produtoTeste = produtoRepository.save(p);
    }

    @Test
    @DisplayName("Cenário A: Custo sobe -> Gera Sugestão -> Gerente APROVA -> Preço Muda")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeFluxoAprovacao() {
        // 1. AÇÃO: Entrada de Estoque com Custo MUITO MAIOR
        // Custo Novo: R$ 60,00 (Era 40,00).
        // Cálculo Esperado: 60 / (1 - 0.5) = R$ 120,00

        EstoqueRequestDTO entrada = new EstoqueRequestDTO();
        entrada.setCodigoBarras("PROD_INFLACAO");
        entrada.setQuantidade(new BigDecimal("10"));
        entrada.setPrecoCusto(new BigDecimal("60.00")); // Aumento grande!
        entrada.setNumeroNotaFiscal("NF-100");
        entrada.setFornecedorCnpj("00000000000191");

        estoqueService.registrarEntrada(entrada);

        // 2. VERIFICAÇÃO: O sistema gerou o alerta?
        List<SugestaoPreco> pendentes = precificacaoService.listarSugestoesPendentes();
        Assertions.assertFalse(pendentes.isEmpty(), "Deveria ter gerado uma sugestão de preço");

        SugestaoPreco sugestao = pendentes.get(0);
        Assertions.assertEquals("PROD_INFLACAO", sugestao.getProduto().getCodigoBarras());
        Assertions.assertEquals(SugestaoPreco.StatusSugestao.PENDENTE, sugestao.getStatus());

        // Verifica se o cálculo sugeriu R$ 120,00
        Assertions.assertTrue(new BigDecimal("120.00").compareTo(sugestao.getPrecoVendaSugerido()) == 0,
                "O preço sugerido deveria ser R$ 120,00 baseado na configuração da loja");

        // 3. AÇÃO DO GERENTE: Aprovar
        precificacaoService.aprovarSugestao(sugestao.getId());

        // 4. VERIFICAÇÃO FINAL: O produto mudou de preço?
        Produto produtoAtualizado = produtoRepository.findById(produtoTeste.getId()).get();

        Assertions.assertTrue(new BigDecimal("120.00").compareTo(produtoAtualizado.getPrecoVenda()) == 0,
                "O preço do produto deveria ter sido atualizado para R$ 120,00");

        // Sugestão sumiu da lista de pendentes?
        SugestaoPreco sugestaoFinal = sugestaoRepository.findById(sugestao.getId()).get();
        Assertions.assertEquals(SugestaoPreco.StatusSugestao.APROVADO, sugestaoFinal.getStatus());

        System.out.println(">>> SUCESSO: Ciclo de Aprovação validado!");
    }

    @Test
    @DisplayName("Cenário B: Custo sobe -> Gera Sugestão -> Gerente REJEITA -> Preço Mantém")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeFluxoRejeicao() {
        // 1. AÇÃO: Entrada com aumento de custo
        EstoqueRequestDTO entrada = new EstoqueRequestDTO();
        entrada.setCodigoBarras("PROD_INFLACAO");
        entrada.setQuantidade(new BigDecimal("5"));
        entrada.setPrecoCusto(new BigDecimal("55.00")); // Aumento
        entrada.setNumeroNotaFiscal("NF-200");
        entrada.setFornecedorCnpj("00000000000191");

        estoqueService.registrarEntrada(entrada);

        // 2. Pega a sugestão gerada
        List<SugestaoPreco> pendentes = precificacaoService.listarSugestoesPendentes();
        Assertions.assertFalse(pendentes.isEmpty());
        Long idSugestao = pendentes.get(0).getId();

        // 3. AÇÃO DO GERENTE: Rejeitar (Decidiu absorver o prejuízo para não perder cliente)
        precificacaoService.rejeitarSugestao(idSugestao);

        // 4. VERIFICAÇÃO FINAL
        Produto produtoInalterado = produtoRepository.findById(produtoTeste.getId()).get();

        Assertions.assertTrue(new BigDecimal("100.00").compareTo(produtoInalterado.getPrecoVenda()) == 0,
                "O preço do produto NÃO deveria ter mudado após rejeição");

        SugestaoPreco sugestaoFinal = sugestaoRepository.findById(idSugestao).get();
        Assertions.assertEquals(SugestaoPreco.StatusSugestao.REJEITADO, sugestaoFinal.getStatus());

        System.out.println(">>> SUCESSO: Ciclo de Rejeição validado!");
    }

    @Test
    @DisplayName("Cenário C: Custo sobe -> Gera Sugestão -> Gerente define PREÇO MANUAL -> Preço Ajustado")
    @WithMockUser(username = "gerente", roles = {"GERENTE"})
    public void testeFluxoAprovacaoManual() {
        // 1. AÇÃO: Entrada com aumento de custo (Gera sugestão de R$ 120,00)
        EstoqueRequestDTO entrada = new EstoqueRequestDTO();
        entrada.setCodigoBarras("PROD_INFLACAO");
        entrada.setQuantidade(new BigDecimal("10"));
        entrada.setPrecoCusto(new BigDecimal("60.00"));
        entrada.setNumeroNotaFiscal("NF-300");
        entrada.setFornecedorCnpj("00000000000191");

        estoqueService.registrarEntrada(entrada);

        // 2. Recupera a sugestão gerada
        List<SugestaoPreco> pendentes = precificacaoService.listarSugestoesPendentes();
        Assertions.assertFalse(pendentes.isEmpty());
        SugestaoPreco sugestao = pendentes.get(0);

        // Valida que o sistema sugeriu 120.00
        Assertions.assertTrue(new BigDecimal("120.00").compareTo(sugestao.getPrecoVendaSugerido()) == 0);

        // 3. AÇÃO DO GERENTE: Aprovar com PREÇO MANUAL (Psicológico)
        // O gerente ignora os 120.00 e define 119.90
        BigDecimal precoPsicologico = new BigDecimal("119.90");
        precificacaoService.aprovarComPrecoManual(sugestao.getId(), precoPsicologico);

        // 4. VERIFICAÇÃO FINAL
        Produto produtoAtualizado = produtoRepository.findById(produtoTeste.getId()).get();

        // O preço deve ser o que o gerente digitou (119.90), e não o sugerido (120.00)
        Assertions.assertTrue(precoPsicologico.compareTo(produtoAtualizado.getPrecoVenda()) == 0,
                "O preço do produto deve ser o valor manual definido pelo gerente (119.90)");

        // Verifica se a sugestão foi marcada como APROVADA e tem a observação no motivo
        SugestaoPreco sugestaoFinal = sugestaoRepository.findById(sugestao.getId()).get();
        Assertions.assertEquals(SugestaoPreco.StatusSugestao.APROVADO, sugestaoFinal.getStatus());

        // Verifica se ficou registrado no log o motivo da alteração
        System.out.println("Motivo Final Audita: " + sugestaoFinal.getMotivo());
        Assertions.assertTrue(sugestaoFinal.getMotivo().contains("Ajustado manualmente"),
                "O motivo deve conter o log de auditoria da alteração manual");

        System.out.println(">>> SUCESSO: Ciclo de Aprovação Manual (Preço Psicológico) validado!");
    }
}