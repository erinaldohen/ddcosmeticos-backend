package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fiscal")
public class NfceController {

    @Autowired
    private NfceService nfceService;

    @Autowired
    private VendaRepository vendaRepository;

    /**
     * Gera e assina o XML da NFC-e para uma venda existente.
     */
    @GetMapping("/nfce/{idVenda}")
    @PreAuthorize("hasAnyRole('GERENTE', 'CAIXA')")
    public ResponseEntity<NfceResponseDTO> gerarNfce(@PathVariable Long idVenda) {

        // 1. Busca a venda no banco
        Venda venda = vendaRepository.findById(idVenda)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada: " + idVenda));

        // 2. Chama o serviço fiscal para gerar o XML assinado
        NfceResponseDTO nfce = nfceService.emitirNfce(venda);

        return ResponseEntity.ok(nfce);
    }
}