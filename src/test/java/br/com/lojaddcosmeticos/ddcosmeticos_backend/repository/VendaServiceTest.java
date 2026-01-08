package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository; // Mantenha no pacote que está, mas mova para .service depois

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario; // Import necessário
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*; // Importa todos os repositórios necessários
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.*; // Importa todos os serviços necessários
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // <--- CORREÇÃO: Evita erro de UnnecessaryStubbing
class VendaServiceTest {

    @InjectMocks
    private VendaService vendaService;

    @Mock private VendaRepository vendaRepository;
    @Mock private ProdutoRepository produtoRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private EstoqueService estoqueService;
    @Mock private FinanceiroService financeiroService;
    @Mock private NfceService nfceService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ConfiguracaoLojaRepository configuracaoLojaRepository;

    // Mocks para simular autenticação
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @Test
    void deveCalcularTotalVendaCorretamente() {
        // 1. Simular Usuário Logado (Obrigatório no VendaService)
        Usuario usuarioMock = new Usuario();
        usuarioMock.setId(1L);
        usuarioMock.setNome("Vendedor Teste");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(usuarioMock);
        SecurityContextHolder.setContext(securityContext);

        // 2. Configurar Cenário (Produto no Banco)
        Produto produtoMock = new Produto();
        produtoMock.setId(1L);
        produtoMock.setPrecoVenda(new BigDecimal("100.00"));
        produtoMock.setCodigoBarras("789");
        produtoMock.setQuantidadeEmEstoque(100); // Estoque suficiente

        // 3. Preparar Item do Pedido
        ItemVendaDTO item = new ItemVendaDTO();
        item.setCodigoBarras("789");
        item.setQuantidade(new BigDecimal("2")); // 2 * 100.00 = 200.00

        // 4. Criar Request
        VendaRequestDTO request = new VendaRequestDTO(
                "12345678900",
                "Cliente Teste",
                FormaDePagamento.DINHEIRO,
                1,
                List.of(item),
                new BigDecimal("10.00"), // Desconto de 10.00
                false,
                false
        );

        // 5. Configurar Comportamento dos Mocks
        // IMPORTANTE: O serviço busca o produto item a item pelo código de barras, não por lista "In"
        when(produtoRepository.findByCodigoBarras("789")).thenReturn(Optional.of(produtoMock));

        // Simula salvamento
        when(vendaRepository.save(any(Venda.class))).thenAnswer(invocation -> {
            Venda v = invocation.getArgument(0);
            v.setId(1L);
            return v;
        });

        // Simula validação de cliente (se documento foi passado)
        when(clienteRepository.findByDocumento(anyString())).thenReturn(Optional.empty());

        // 6. Executar o método real (Agora descomentado!)
        Venda vendaRealizada = vendaService.realizarVenda(request);

        // 7. Validações (Asserts)
        // Valor total esperado: (2 * 100) - 10 = 190.00
        BigDecimal esperado = new BigDecimal("190.00");
        BigDecimal obtido = vendaRealizada.getTotalVenda().subtract(vendaRealizada.getDescontoTotal());

        // Compara ignorando casas decimais extras
        assert obtido.compareTo(esperado) == 0 : "Valor total incorreto. Esperado: " + esperado + ", Obtido: " + obtido;
    }
}