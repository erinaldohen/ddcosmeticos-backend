package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfceService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.VendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fiscal")
public class NfceController {

    @Autowired
    private NfceService nfceService;

    @Autowired
    private VendaService vendaService; // Substituiu o Repository

    @GetMapping("/nfce/{idVenda}")
    @PreAuthorize("hasAnyRole('GERENTE', 'CAIXA')")
    public ResponseEntity<NfceResponseDTO> gerarNfce(@PathVariable Long idVenda) {

        // O Service já trata a exceção ResourceNotFoundException se não achar
        Venda venda = vendaService.buscarVendaComItens(idVenda);

        NfceResponseDTO resultado = nfceService.emitirNfce(venda);

        return ResponseEntity.ok(resultado);
    }
}