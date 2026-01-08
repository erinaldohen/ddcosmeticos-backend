package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.controller.ProdutoController; // Importante
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest; // Usar WebMvcTest em vez de SpringBootTest
// Se MockitoBean não funcionar, use @MockBean (padrão Spring Boot < 3.4)
// Se estiver no 3.4+, use: import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Carrega APENAS o ProdutoController. Muito mais rápido e menos propenso a erro.
@WebMvcTest(ProdutoController.class)
// Importante: Se você tiver uma classe SecurityConfig complexa, talvez precise importá-la
// @Import(SecurityConfig.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean // Tente MockBean primeiro, é mais compatível com WebMvcTest antigo
    private ProdutoService produtoService;

    @MockitoBean
    private br.com.lojaddcosmeticos.ddcosmeticos_backend.security.JwtService jwtService;

    @MockitoBean
    private br.com.lojaddcosmeticos.ddcosmeticos_backend.handler.SecurityFilter securityFilter;

    // Se o seu SecurityConfig exigir UserDetailsService, mocke ele também
    // @MockBean
    // private UserDetailsService userDetailsService;
    // @MockBean
    // private JwtService jwtService; // Se tiver filtro JWT

    @Test
    @DisplayName("GET /produtos - Deve retornar 200")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deveListarProdutosComSeguranca() throws Exception {
        ProdutoListagemDTO dto = new ProdutoListagemDTO(
                1L, "Teste Shampoo", new BigDecimal("25.90"), null,
                10, true, "123456", "Marca X", "NCM"
        );

        when(produtoService.listarResumo(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/produtos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].descricao").value("Teste Shampoo"));
    }

    @Test
    @DisplayName("DELETE /produtos - Deve negar acesso (403)")
    @WithMockUser(roles = "USUARIO")
    void deveBloquearExclusaoParaUsuarioComum() throws Exception {
        mockMvc.perform(delete("/api/v1/produtos/123"))
                .andExpect(status().isForbidden());
    }
}