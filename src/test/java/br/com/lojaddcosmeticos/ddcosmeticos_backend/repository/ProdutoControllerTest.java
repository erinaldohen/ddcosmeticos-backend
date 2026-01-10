package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository; // Ajuste se seu pacote for diferente

import br.com.lojaddcosmeticos.ddcosmeticos_backend.controller.ProdutoController;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.security.JwtService; // Importante
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao.CosmosService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProdutoController.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // --- MOCKS DOS SERVIÇOS USADOS NO CONTROLLER ---
    @MockitoBean private ProdutoService produtoService;
    @MockitoBean private CosmosService cosmosService;
    @MockitoBean private PrecificacaoService precificacaoService;
    @MockitoBean private EstoqueService estoqueService;
    @MockitoBean private ArquivoService arquivoService;
    @MockitoBean private AuditoriaService auditoriaService;

    // --- MOCKS OBRIGATÓRIOS PARA A SEGURANÇA (SecurityFilter) ---
    // O SecurityFilter precisa destes beans para inicializar, senão o contexto falha.
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UsuarioRepository usuarioRepository;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void deveListarProdutosComSeguranca() throws Exception {
        // Cenário
        ProdutoListagemDTO dto = new ProdutoListagemDTO(
                1L, "TESTE", BigDecimal.TEN, null, 10, true, "123", "MARCA", "NCM"
        );

        when(produtoService.listarResumo(any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 10, Sort.by("descricao")), 1));

        // Ação e Validação
        mockMvc.perform(get("/api/v1/produtos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USUARIO"})
    void deveBloquearExclusaoParaUsuarioComum() throws Exception {
        // Tenta deletar/inativar (Endpoint protegido)
        mockMvc.perform(delete("/api/v1/produtos/EAN123"))
                .andExpect(status().isForbidden());
    }
}