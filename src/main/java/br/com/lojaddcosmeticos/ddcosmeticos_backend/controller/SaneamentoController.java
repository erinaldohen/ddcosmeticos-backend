package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/saneamento")
public class SaneamentoController {

    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private FornecedorService fornecedorService;

    // Execute este endpoint UMA VEZ para corrigir a base
    @PostMapping("/corrigir-fornecedores")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<String> corrigirFornecedoresBugados() {
        List<Fornecedor> todos = fornecedorRepository.findAll();
        int corrigidos = 0;
        StringBuilder log = new StringBuilder("Relatório de Correção:\n");

        for (Fornecedor f : todos) {
            // DETECÇÃO DO BUG: Nome Fantasia contém apenas números (é o CNPJ errado)
            boolean nomeEhNumero = f.getNomeFantasia().matches("^\\d+$");
            boolean cnpjPareceInvalido = f.getCnpj() == null || f.getCnpj().length() < 10 || !f.getCnpj().matches("^\\d+$");

            if (nomeEhNumero) {
                // O CNPJ verdadeiro está no campo nome fantasia!
                String cnpjReal = f.getNomeFantasia();

                // Busca dados corretos na API
                try {
                    ConsultaCnpjDTO dados = fornecedorService.consultarDadosCnpj(cnpjReal);
                    if (dados != null) {
                        f.setCnpj(cnpjReal);
                        f.setRazaoSocial(dados.getRazaoSocial());
                        f.setNomeFantasia(dados.getNomeFantasia());
                        f.setLogradouro(dados.getLogradouro());
                        f.setNumero(dados.getNumero());
                        f.setBairro(dados.getBairro());
                        f.setCidade(dados.getMunicipio());
                        f.setUf(dados.getUf());
                        f.setCep(dados.getCep());

                        fornecedorRepository.save(f);
                        corrigidos++;
                        log.append("✅ Corrigido: ").append(cnpjReal).append(" -> ").append(dados.getNomeFantasia()).append("\n");
                    }
                } catch (Exception e) {
                    log.append("❌ Erro ao corrigir ").append(cnpjReal).append(": ").append(e.getMessage()).append("\n");
                }
            }
        }

        return ResponseEntity.ok("Processo finalizado. Total corrigidos: " + corrigidos + "\n" + log.toString());
    }
}