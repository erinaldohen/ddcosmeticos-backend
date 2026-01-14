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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
            if (auth == null || !auth.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String login = auth.getName();

            // Busca usuário
            Usuario usuario = usuarioRepository.findByMatricula(login)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login novamente."));

            // --- USO SEGURO DO FINDFIRST ---
            return caixaRepository.findFirstByUsuarioAberturaAndStatus(usuario, StatusCaixa.ABERTO)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.noContent().build()); // Retorna 204 (Fechado)

        } catch (Exception e) {
            log.error("Erro ao verificar status (Recuperando de falha crítica): ", e);
            // Se der qualquer erro bizarro, assumimos que o caixa está fechado para não travar o usuário
            return ResponseEntity.noContent().build();
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