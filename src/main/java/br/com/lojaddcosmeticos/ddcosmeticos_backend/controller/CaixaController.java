package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentacaoCaixaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/caixa")
@Tag(name = "Caixa", description = "Gestão operacional de caixa")
public class CaixaController {

    @Autowired private CaixaService caixaService;
    @Autowired private CaixaDiarioRepository caixaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private MovimentacaoCaixaRepository movimentacaoRepository;

    // --- CORREÇÃO DO 403 AQUI ---
    // isAuthenticated() permite qualquer usuário logado (Admin, Caixa, Gerente)
    // Isso evita conflitos de nomes de Role (ex: ROLE_ADMIN vs ADMIN)
    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CaixaDiarioDTO> verificarStatus() {
        return ResponseEntity.ok(caixaService.buscarStatusAtual());
    }

    @PostMapping("/abrir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CaixaDiario> abrirCaixa(@RequestBody Map<String, BigDecimal> payload) {
        BigDecimal saldoInicial = payload.get("saldoInicial");
        return ResponseEntity.ok(caixaService.abrirCaixa(saldoInicial));
    }

    @GetMapping("/historico")
    public ResponseEntity<List<CaixaDiario>> listarHistorico(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim
    ) {
        if (inicio != null && fim != null) {
            return ResponseEntity.ok(caixaRepository.findByDataAberturaBetweenOrderByDataAberturaDesc(inicio, fim));
        }
        return ResponseEntity.ok(caixaRepository.findAllByOrderByDataAberturaDesc());
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
        Usuario usuario = usuarioRepository.findByMatricula(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));

        CaixaDiario caixa = caixaRepository.findFirstByUsuarioAberturaAndStatus(usuario, br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa.ABERTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caixa fechado."));

        dto.setCaixa(caixa);
        dto.setDataHora(LocalDateTime.now());
        dto.setUsuarioResponsavel(usuario.getNome());

        return ResponseEntity.ok(movimentacaoRepository.save(dto));
    }
}