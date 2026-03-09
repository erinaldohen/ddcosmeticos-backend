package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.VendaPerdida;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaPerdidaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaRelatorioService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/caixas")
@Tag(name = "Caixa", description = "Gestão operacional de caixa")
@RequiredArgsConstructor
public class CaixaController {

    private final CaixaService caixaService;
    private final CaixaDiarioRepository caixaRepository;
    private final CaixaRelatorioService relatorioService;
    private final VendaRepository vendaRepository;
    private final VendaPerdidaRepository vendaPerdidaRepository;
    // --- OPERACIONAL ---

    @GetMapping("/alertas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CaixaDiarioDTO>> getAlertasDeRisco() {
        return ResponseEntity.ok(caixaService.buscarAlertasRiscoDashboard());
    }

    // ==================================================================================
    // NOVO: MOTOR DE SUGESTÕES DE VENDA (IA DE BALCÃO)
    // ==================================================================================

    @GetMapping("/sugestao-ia/{produtoId}")
    @Operation(summary = "Gera sugestões de cross-sell baseadas no carrinho atual")
    @PreAuthorize("isAuthenticated()") // Qualquer operador pode aceder
    public ResponseEntity<List<String>> getSugestaoIA(@PathVariable Long produtoId) {
        try {
            List<String> sugestoes = vendaRepository.buscarSugestoesParaProduto(produtoId);
            return ResponseEntity.ok(sugestoes);
        } catch (Exception e) {
            log.error("Erro ao gerar sugestão de IA para o produto {}: {}", produtoId, e.getMessage());
            // Em caso de erro do banco de dados, retorna lista vazia para não travar o PDV
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/fechar")
    public ResponseEntity<Void> fecharCaixa(
            @Valid @RequestBody FechamentoCaixaRequestDTO request) {
        // CORREÇÃO: Agora passamos os dois campos para o Service
        caixaService.fecharCaixa(request.valorFisicoInformado(), request.justificativaDiferenca());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<?> verificarStatusCaixa() {
        try {
            CaixaDiarioDTO caixa = caixaService.buscarStatusAtual();

            if (caixa != null && "ABERTO".equals(caixa.status())) {
                return ResponseEntity.ok(Map.of(
                        "status", "ABERTO",
                        "aberto", true,
                        "caixa", caixa
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "status", "FECHADO",
                        "aberto", false
                ));
            }
        } catch (Exception e) {
            log.error("Erro crítico ao verificar status do caixa: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                    "status", "FECHADO",
                    "aberto", false
            ));
        }
    }

    @PostMapping("/abrir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CaixaDiarioDTO> abrirCaixa(@RequestBody Map<String, BigDecimal> payload) {
        BigDecimal saldoInicial = payload.get("saldoInicial");
        return ResponseEntity.ok(caixaService.abrirCaixa(saldoInicial));
    }

    // --- MOVIMENTAÇÕES ---

    @GetMapping("/motivos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getMotivosFrequentes() {
        return ResponseEntity.ok(caixaService.listarMotivosFrequentes());
    }

    @PostMapping("/sangria")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> realizarSangria(@RequestBody MovimentacaoDTO dto) {
        caixaService.realizarSangria(dto.getValor(), dto.getMotivo());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/suprimento")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> realizarSuprimento(@RequestBody MovimentacaoDTO dto) {
        caixaService.realizarSuprimento(dto.getValor(), dto.getMotivo());
        return ResponseEntity.ok().build();
    }

    // --- HISTÓRICO E RELATÓRIOS ---

    @GetMapping
    public ResponseEntity<Page<CaixaDiarioDTO>> listarTodos(
            Pageable pageable,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        return ResponseEntity.ok(caixaService.listarHistoricoPaginado(inicio, fim, pageable));
    }

    @GetMapping("/diario")
    public ResponseEntity<List<MovimentacaoCaixa>> getHistoricoDiario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        LocalDate target = (data != null) ? data : LocalDate.now();
        return ResponseEntity.ok(caixaService.buscarHistorico(target, target));
    }

    @GetMapping("/relatorio/pdf")
    public void gerarRelatorioPdf(
            HttpServletResponse response,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) throws IOException {

        if (relatorioService == null) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Serviço de relatório não configurado");
            return;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=relatorio_caixas.pdf");

        LocalDate dInicio = (inicio != null) ? inicio : LocalDate.now().minusDays(30);
        LocalDate dFim = (fim != null) ? fim : LocalDate.now();

        List<CaixaDiario> lista = caixaRepository.findByDataAberturaBetweenOrderByDataAberturaDesc(
                dInicio.atStartOfDay(),
                dFim.atTime(23, 59, 59)
        );

        relatorioService.exportarPdf(response, lista, dInicio.toString(), dFim.toString());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true) // <-- ESSA ANOTAÇÃO SALVA O DIA! Mantém a conexão aberta para ler o Usuário
    public ResponseEntity<CaixaDiarioDTO> buscarPorId(@PathVariable Long id) {
        return caixaRepository.findById(id)
                .map(caixaService::converterParaDTOCompleto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    // ==================================================================================
    // RUPTURA: REGISTO DE VENDA PERDIDA NO PDV
    // ==================================================================================
    @PostMapping("/venda-perdida")
    @Operation(summary = "Registra um produto que o cliente pediu mas não tinha no estoque")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> registrarVendaPerdida(@RequestBody Map<String, String> payload) {
        VendaPerdida vp = new VendaPerdida();
        vp.setProdutoProcurado(payload.get("produto"));
        vp.setDataRegistro(LocalDateTime.now());

        // (Opcional) Capturar o nome do utilizador autenticado
        // vp.setOperador(usuarioAutenticado.getNome());

        vendaPerdidaRepository.save(vp);
        return ResponseEntity.ok().build();
    }
}