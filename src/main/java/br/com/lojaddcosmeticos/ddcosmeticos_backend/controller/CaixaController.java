package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentacaoCaixaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    @GetMapping("/status")
    public ResponseEntity<CaixaDiario> verificarStatus() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            // Se não houver autenticação, retorna 401 explicitamente
            if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String login = auth.getName();
            Usuario usuario = usuarioRepository.findByMatricula(login).orElse(null);

            if (usuario == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Busca o caixa. Se não houver, o orElse retorna 204 No Content (sem erro 500)
            return caixaRepository.findFirstByUsuarioAberturaAndStatus(usuario, StatusCaixa.ABERTO)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.noContent().build());

        } catch (Exception e) {
            log.error("Erro ao verificar status: ", e);
            return ResponseEntity.noContent().build(); // Retorna vazio em vez de erro 500
        }
    }

    @PostMapping("/abrir")
    public ResponseEntity<CaixaDiario> abrirCaixa(@RequestBody Map<String, BigDecimal> payload) {
        BigDecimal saldoInicial = payload.get("saldoInicial");
        log.info("Tentativa de abrir caixa com saldo inicial: {}", saldoInicial);

        if (saldoInicial == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O saldo inicial é obrigatório.");
        }

        return ResponseEntity.ok(caixaService.abrirCaixa(saldoInicial));
    }

    // br.com.lojaddcosmeticos.ddcosmeticos_backend.controller.CaixaController
    @GetMapping("/historico")
    public ResponseEntity<List<CaixaDiario>> listarHistorico(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim
    ) {
        // Se ambas as datas estiverem presentes, filtra. Caso contrário, traz tudo ordenado.
        if (inicio != null && fim != null) {
            return ResponseEntity.ok(caixaRepository.findByDataAberturaBetweenOrderByDataAberturaDesc(inicio, fim));
        }

        // Agora este método existe e a compilação passará
        return ResponseEntity.ok(caixaRepository.findAllByOrderByDataAberturaDesc());
    }

    @PostMapping("/fechar")
    public ResponseEntity<CaixaDiario> fecharCaixa(@RequestBody Map<String, BigDecimal> payload) {
        return ResponseEntity.ok(caixaService.fecharCaixa(payload.get("saldoFinalInformado")));
    }

    @PostMapping("/movimentacao")
    public ResponseEntity<MovimentacaoCaixa> realizarMovimentacao(@RequestBody MovimentacaoCaixa dto) {
        String login = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByMatricula(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));

        CaixaDiario caixa = caixaRepository.findFirstByUsuarioAberturaAndStatus(usuario, StatusCaixa.ABERTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caixa fechado."));

        dto.setCaixa(caixa);
        dto.setDataHora(LocalDateTime.now());
        dto.setUsuarioResponsavel(usuario.getNome());

        return ResponseEntity.ok(movimentacaoRepository.save(dto));
    }
}