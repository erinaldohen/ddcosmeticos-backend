package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Importante para segurança
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
    private VendaRepository vendaRepository;

    @GetMapping("/nfce/{idVenda}")
    @PreAuthorize("hasAnyRole('GERENTE', 'CAIXA')") // Garante segurança
    public ResponseEntity<NfceResponseDTO> gerarNfce(@PathVariable Long idVenda) {

        // CORREÇÃO: Usa o método que busca os itens junto (JOIN FETCH)
        Venda venda = vendaRepository.findByIdWithItens(idVenda)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada: " + idVenda));

        NfceResponseDTO resultado = nfceService.emitirNfce(venda);

        return ResponseEntity.ok(resultado);
    }
}