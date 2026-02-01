package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentacaoCaixaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FinanceiroService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
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
    private final UsuarioRepository usuarioRepository;
    private final MovimentacaoCaixaRepository movimentacaoRepository;
    private final FinanceiroService financeiroService;

    // --- 1. LISTAGEM COM PAGINAÇÃO E FILTRO DE DATA ---
    @GetMapping
    public ResponseEntity<Page<CaixaDiario>> listarTodos(
            Pageable pageable,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        if (inicio != null && fim != null) {
            // Converte para LocalDateTime para pegar o dia completo (00:00 até 23:59)
            LocalDateTime dataInicio = inicio.atStartOfDay();
            LocalDateTime dataFim = fim.atTime(23, 59, 59);

            return ResponseEntity.ok(caixaRepository.findByDataAberturaBetween(dataInicio, dataFim, pageable));
        }

        return ResponseEntity.ok(caixaRepository.findAll(pageable));
    }

    // --- 2. BUSCA DETALHADA POR ID (ESSENCIAL PARA O BOTÃO 'OLHO') ---
    @GetMapping("/{id}")
    public ResponseEntity<CaixaDiario> buscarPorId(@PathVariable Long id) {
        return caixaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- 3. STATUS ATUAL ---
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CaixaDiarioDTO> verificarStatus() {
        return ResponseEntity.ok(caixaService.buscarStatusAtual());
    }

    // --- 4. MOVIMENTAÇÃO DIÁRIA (LEGADO/AUXILIAR) ---
    @GetMapping("/diario")
    public ResponseEntity<List<MovimentacaoCaixa>> getHistoricoDiario(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data
    ) {
        LocalDate target = (data != null) ? data : LocalDate.now();
        return ResponseEntity.ok(caixaService.buscarHistorico(target, target));
    }

    // --- 5. OPERAÇÕES DE CAIXA ---
    @PostMapping("/abrir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CaixaDiario> abrirCaixa(@RequestBody Map<String, BigDecimal> payload) {
        BigDecimal saldoInicial = payload.get("saldoInicial");
        return ResponseEntity.ok(caixaService.abrirCaixa(saldoInicial));
    }

    @PostMapping("/fechar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CaixaDiario> fecharCaixa(@RequestBody Map<String, BigDecimal> payload) {
        return ResponseEntity.ok(caixaService.fecharCaixa(payload.get("saldoFinalInformado")));
    }

    @PostMapping("/movimentacao")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MovimentacaoCaixa> realizarMovimentacao(@RequestBody MovimentacaoCaixa dto) {
        String login = SecurityContextHolder.getContext().getAuthentication().getName();

        Usuario usuario = usuarioRepository.findByMatriculaOrEmail(login, login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));

        CaixaDiario caixa = caixaRepository.findFirstByUsuarioAberturaAndStatus(usuario, br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa.ABERTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caixa fechado."));

        dto.setCaixa(caixa);
        dto.setDataHora(LocalDateTime.now());
        dto.setUsuarioResponsavel(usuario.getNome());

        return ResponseEntity.ok(movimentacaoRepository.save(dto));
    }

    @PostMapping("/movimentar")
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    public ResponseEntity<MovimentacaoCaixa> realizarMovimentacaoManual(
            @RequestBody MovimentacaoDTO dto,
            Principal principal) {
        String usuario = (principal != null) ? principal.getName() : "SISTEMA_LOCAL";
        return ResponseEntity.ok(financeiroService.registrarMovimentacaoManual(dto, usuario));
    }
}