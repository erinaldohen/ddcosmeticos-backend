package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FinanceiroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/caixa")
public class MovimentacaoCaixaController {

    @Autowired
    private FinanceiroService financeiroService;

    @PostMapping("/movimentar")
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE')")
    public ResponseEntity<MovimentacaoCaixa> realizarMovimentacao(
            @RequestBody MovimentacaoDTO dto,
            Principal principal) {

        String usuario = (principal != null) ? principal.getName() : "SISTEMA_LOCAL";

        // Agora o método 'registrarMovimentacaoManual' existe no service
        return ResponseEntity.ok(financeiroService.registrarMovimentacaoManual(dto, usuario));
    }
}