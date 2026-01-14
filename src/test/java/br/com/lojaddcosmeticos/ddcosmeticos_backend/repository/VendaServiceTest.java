package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository; // Pacote correto para teste de serviço

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ResumoFiscalCarrinhoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.*;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendaServiceTest {

    @InjectMocks
    private VendaService vendaService;

    @Mock private VendaRepository vendaRepository;
    @Mock private ProdutoRepository produtoRepository;

    // IMPORTANTE: Adicionado Mock da Calculadora Fiscal (Necessário após as atualizações recentes)
    @Mock private CalculadoraFiscalService calculadoraFiscalService;

    // Mocks para simular autenticação
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @Test
    void deveCalcularTotalVendaCorretamente() {
        // 1. Simular Usuario Logado
        Usuario usuarioMock = new Usuario();
        usuarioMock.setId(1L);
        usuarioMock.setNome("Vendedor Teste");

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(usuarioMock);
        SecurityContextHolder.setContext(securityContext);

        // 2. Configurar Cenário (Produto no Banco)
        Produto produtoMock = new Produto();
        produtoMock.setId(1L);
        produtoMock.setDescricao("Produto Teste");
        produtoMock.setPrecoVenda(new BigDecimal("100.00"));
        produtoMock.setCodigoBarras("789");
        produtoMock.setQuantidadeEmEstoque(100);

        // 3. Preparar Item do Pedido (Usando construtor do Record)
        // Ordem: idProduto, codigoBarras, quantidade, precoUnitario
        ItemVendaDTO item = new ItemVendaDTO(null, "789", new BigDecimal("2"), null);

        // 4. Criar Request (Usando construtor do Record)
        VendaRequestDTO request = new VendaRequestDTO(
                "12345678900",
                "Cliente Teste",
                FormaDePagamento.DINHEIRO,
                1, // Parcelas
                List.of(item),
                new BigDecimal("10.00"), // Desconto
                false, // Apenas com NF entrada
                false  // Orçamento
        );

        // 5. Configurar Comportamento dos Mocks
        when(produtoRepository.findByCodigoBarras("789")).thenReturn(Optional.of(produtoMock));
        // Fallback caso busque por ID
        when(produtoRepository.findById(any())).thenReturn(Optional.of(produtoMock));

        // Mock da Calculadora Fiscal (Essencial para não dar NullPointerException no valorIbs, etc.)
        when(calculadoraFiscalService.calcularTotaisCarrinho(anyList())).thenReturn(
                new ResumoFiscalCarrinhoDTO(
                        new BigDecimal("200.00"), // Total Venda
                        BigDecimal.ZERO, // IBS
                        BigDecimal.ZERO, // CBS
                        BigDecimal.ZERO, // IS
                        new BigDecimal("200.00"), // Liquido
                        BigDecimal.ZERO // Alíquota
                )
        );

        // Simula salvamento e retorno da entidade com ID
        when(vendaRepository.save(any(Venda.class))).thenAnswer(invocation -> {
            Venda v = invocation.getArgument(0);
            v.setIdVenda(1L); // Simula o ID gerado pelo banco
            return v;
        });

        // 6. Executar o método real
        // CORREÇÃO: O serviço retorna um DTO, não a entidade Venda
        VendaResponseDTO response = vendaService.realizarVenda(request);

        // 7. Validações (Asserts)
        // Cálculo esperado: (2 qtd * 100 preço) - 10 desconto = 190.00
        BigDecimal totalEsperado = new BigDecimal("190.00");

        // Em records usa-se .valorTotal() e não .getValorTotal()
        BigDecimal totalObtido = response.valorTotal();

        assert totalObtido.compareTo(totalEsperado) == 0
                : "Valor total incorreto. Esperado: " + totalEsperado + ", Obtido: " + totalObtido;

        // Verifica se chamou a calculadora fiscal
        verify(calculadoraFiscalService, times(1)).calcularTotaisCarrinho(anyList());
    }
}