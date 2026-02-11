package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaRelatorioService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // --- OPERACIONAL ---

    @GetMapping("/status")
    public ResponseEntity<?> verificarStatusCaixa() {
        try {
            // CORREÇÃO: Usa o método existente no Service que retorna DTO
            CaixaDiarioDTO caixa = caixaService.buscarStatusAtual();

            if (caixa != null && "ABERTO".equals(caixa.status())) { // Acessa campo do record sem get
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
            return ResponseEntity.ok(Map.of(
                    "status", "FECHADO",
                    "aberto", false
            ));
        }
    }

    @PostMapping("/abrir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CaixaDiario> abrirCaixa(@RequestBody Map<String, BigDecimal> payload) {
        BigDecimal saldoInicial = payload.get("saldoInicial");
        // O método abrirCaixa retorna a Entidade CaixaDiario
        return ResponseEntity.ok(caixaService.abrirCaixa(saldoInicial));
    }

    @PostMapping("/{id}/fechar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CaixaDiario> fecharCaixa(@PathVariable Long id, @RequestBody FechamentoCaixaDTO dto) {
        // CORREÇÃO: Acessa o campo correto do Record (sem get e com o nome exato)
        return ResponseEntity.ok(caixaService.fecharCaixa(dto.saldoFinalDinheiroEmEspecie()));
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
    public ResponseEntity<Page<CaixaDiario>> listarTodos(
            Pageable pageable,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        if (inicio != null && fim != null) {
            return ResponseEntity.ok(caixaRepository.findByDataAberturaBetween(
                    inicio.atStartOfDay(),
                    fim.atTime(23, 59, 59),
                    pageable
            ));
        }
        return ResponseEntity.ok(caixaRepository.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaixaDiario> buscarPorId(@PathVariable Long id) {
        return caixaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
}