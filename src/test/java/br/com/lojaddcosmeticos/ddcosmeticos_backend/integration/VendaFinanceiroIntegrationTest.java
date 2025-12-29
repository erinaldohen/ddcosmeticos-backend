package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
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
public class VendaFinanceiroIntegrationTest {

    @Autowired private VendaService vendaService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @Test
    @DisplayName("Venda CRÉDITO 3x: Deve gerar 3 registros")
    @WithMockUser(username = "caixa", roles = {"CAIXA"})
    public void testeVendaCreditoAntecipado() {
        // 1. Criar Usuário (necessário pois o Service busca no banco)
        Usuario caixa = new Usuario();
        caixa.setMatricula("caixa");
        caixa.setSenha("123");
        caixa.setPerfil(PerfilDoUsuario.ROLE_USUARIO);
        caixa.setNome("Caixa Teste");
        usuarioRepository.save(caixa);

        // 2. Setup Produto
        Produto p = new Produto();
        p.setCodigoBarras("789_PERFUME_TOP");
        p.setDescricao("PERFUME CARO");
        p.setPrecoVenda(new BigDecimal("100.00"));
        p.setQuantidadeEmEstoque(10);
        p.setEstoqueFiscal(10);
        p.setEstoqueNaoFiscal(0);
        p.setPrecoMedioPonderado(new BigDecimal("50.00"));
        p.setPrecoCusto(new BigDecimal("50.00"));
        p.setAtivo(true);
        produtoRepository.save(p);

        // 3. Preparar Item
        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodigoBarras("789_PERFUME_TOP");
        item.setQuantidade(new BigDecimal("3"));

        // 4. Criar DTO (Ordem exata do Record atualizado)
        VendaRequestDTO dto = new VendaRequestDTO(
                "00000000000",          // clienteDocumento
                "Cliente Teste",        // clienteNome
                FormaDePagamento.CREDITO,
                3,                      // quantidadeParcelas
                List.of(item),          // itens
                BigDecimal.ZERO,        // descontoTotal
                false,                  // apenasItensComNfEntrada
                false                   // ehOrcamento (NOVO CAMPO)
        );

        // 5. Execução
        Venda vendaSalva = vendaService.realizarVenda(dto);

        // 6. Validações
        Produto pAtualizado = produtoRepository.findByCodigoBarras("789_PERFUME_TOP").get();
        Assertions.assertEquals(7, pAtualizado.getQuantidadeEmEstoque());

        List<ContaReceber> recebiveis = contaReceberRepository.findByIdVendaRef(vendaSalva.getId());
        Assertions.assertEquals(3, recebiveis.size());

        for (ContaReceber conta : recebiveis) {
            Assertions.assertNotNull(conta.getDataVencimento());
            Assertions.assertTrue(new BigDecimal("100.00").compareTo(conta.getValorTotal()) == 0);
            Assertions.assertEquals(StatusConta.PENDENTE, conta.getStatus());
        }
    }
}