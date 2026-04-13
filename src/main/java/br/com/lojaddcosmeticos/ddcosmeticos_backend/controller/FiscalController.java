package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ValidacaoFiscalDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FiscalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/fiscal")
public class FiscalController {

    @Autowired
    private FiscalService fiscalService;

    /**
     * Endpoint chamado pelo Frontend ao digitar o NCM no cadastro de produtos.
     * Ele avalia os dados e devolve as flags tributárias corretas.
     */
    @PostMapping("/validar")
    public ResponseEntity<ValidacaoFiscalDTO> validarDadosFiscais(@RequestBody Map<String, String> payload) {
        String descricao = payload.get("descricao");
        String ncm = payload.get("ncm");

        ValidacaoFiscalDTO resultado = fiscalService.validarProduto(descricao, ncm);

        return ResponseEntity.ok(resultado);
    }
}