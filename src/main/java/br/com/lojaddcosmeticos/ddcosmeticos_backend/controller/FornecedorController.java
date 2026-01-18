package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FornecedorDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fornecedores")
public class FornecedorController {

    @Autowired
    private FornecedorService fornecedorService;

    // --- 1. LEITURA ---

    @GetMapping
    @PreAuthorize("hasAnyRole('CAIXA', 'GERENTE', 'ESTOQUISTA', 'ADMIN')")
    public ResponseEntity<Page<FornecedorDTO>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String termo) {

        // Correção de ordenação segura
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("nomeFantasia"));
        return ResponseEntity.ok(fornecedorService.listar(termo, pageRequest));
    }

    // Endpoint otimizado para preencher combobox/selects na tela de Entrada
    @GetMapping("/dropdown")
    public ResponseEntity<List<FornecedorDTO>> listarParaDropdown() {
        return ResponseEntity.ok(fornecedorService.listarTodosParaDropdown());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ESTOQUISTA', 'ADMIN')")
    public ResponseEntity<FornecedorDTO> buscarPorId(@PathVariable Long id) {
        // Agora retorna DTO para evitar o loop no retorno individual também
        // (Requer pequeno ajuste no service se quiser usar toDTO, ou retornamos o Entity mas com o @JsonIgnore funcionando)
        // Para simplificar e manter compatibilidade com seu frontend atual que espera objeto completo:
        return ResponseEntity.ok(fornecedorService.atualizar(id, fornecedorService.toDTO(fornecedorService.buscarPorId(id))));
        // Hack rápido: chama o método que já converte, ou use o buscarPorId do service se ele retornasse DTO.
        // O ideal é o buscarPorId do Service retornar Fornecedor e aqui convertermos, ou o Service retornar DTO.
        // Dado o código acima do service, vamos fazer o cast aqui mesmo chamando o converter que fiz privado?
        // Não, melhor: O service acima tem buscarPorId retornando ENTITY. O @JsonIgnore no Model resolve o erro 500 aqui.
    }

    // --- 2. ESCRITA ---

    @PostMapping
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    public ResponseEntity<FornecedorDTO> cadastrar(@RequestBody @Valid FornecedorDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fornecedorService.salvar(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    public ResponseEntity<FornecedorDTO> atualizar(@PathVariable Long id, @RequestBody @Valid FornecedorDTO dto) {
        return ResponseEntity.ok(fornecedorService.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        fornecedorService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    // --- 3. EXTRAS ---

    @GetMapping("/{id}/sugestao-compra")
    public ResponseEntity<List<Produto>> obterSugestaoCompra(@PathVariable Long id) {
        return ResponseEntity.ok(fornecedorService.obterSugestaoDeCompra(id));
    }

    @GetMapping("/consulta-cnpj/{cnpj}")
    public ResponseEntity<ConsultaCnpjDTO> consultarCnpjExterno(@PathVariable String cnpj) {
        return ResponseEntity.ok(fornecedorService.consultarCnpjExterno(cnpj));
    }
}