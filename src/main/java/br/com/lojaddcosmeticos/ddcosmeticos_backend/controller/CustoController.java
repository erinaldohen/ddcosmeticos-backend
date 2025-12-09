// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/controller/CustoController.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EntradaNFRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CustoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/custo")
public class CustoController {

    @Autowired
    private CustoService custoService;

    /**
     * Endpoint para registrar a entrada de uma Nota Fiscal e recalcular o PMP dos produtos.
     * @param requestDTO Dados da NF de entrada.
     * @return 200 OK.
     */
    @PostMapping("/entrada")
    public ResponseEntity<Void> registrarEntradaNF(@RequestBody EntradaNFRequestDTO requestDTO) {
        // O CustoService fará a validação, recálculo e auditoria
        custoService.registrarEntradaNF(requestDTO);
        return ResponseEntity.ok().build();
    }
}