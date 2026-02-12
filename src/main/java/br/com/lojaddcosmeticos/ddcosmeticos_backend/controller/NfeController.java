package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fiscal/nfe")
@Tag(name = "Fiscal NF-e (Modelo 55)", description = "Emissão de Notas para Atacado/Empresas e Status Sefaz")
public class NfeController {

    @Autowired
    private NfeService nfeService;

    // REMOVIDO: private ConfiguracoesNfe configuracoesNfe; (Causa do erro de inicialização)

    @GetMapping("/status")
    @Operation(summary = "Verificar Status SEFAZ", description = "Testa a comunicação com a SEFAZ.")
    public ResponseEntity<String> verificarStatusSefaz() {
        try {
            // A lógica deve ficar no Service, que tem acesso ao Certificado no Banco
            String status = nfeService.consultarStatusSefaz();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erro ao consultar SEFAZ: " + e.getMessage());
        }
    }

    @PostMapping("/emitir/{idVenda}")
    public ResponseEntity<?> emitirNfe(@PathVariable Long idVenda) {
        System.out.println("Recebida solicitação de NFe para venda ID: " + idVenda);
        try {
            NfceResponseDTO retorno = nfeService.emitirNfeModelo55(idVenda);
            return ResponseEntity.ok(retorno);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Erro ao emitir: " + e.getMessage());
        }
    }
}