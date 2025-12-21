package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class FornecedorService {

    @Autowired
    private FornecedorRepository fornecedorRepository;

    /**
     * Método utilitário para buscar ou criar rápido (usado em entradas de estoque/xml)
     */
    @Transactional
    public Fornecedor buscarOuCriarRapido(String documento) {
        String cpfCnpjSemPontuacao = documento.replaceAll("\\D", "");

        return fornecedorRepository.findByCpfOuCnpj(documento)
                .or(() -> fornecedorRepository.findByCpfOuCnpj(cpfCnpjSemPontuacao))
                .orElseGet(() -> {
                    Fornecedor novo = new Fornecedor();
                    novo.setCpfOuCnpj(documento);
                    novo.setRazaoSocial("Fornecedor " + documento);
                    novo.setNomeFantasia("Fornecedor " + documento);
                    novo.setAtivo(true);
                    novo.setTipoPessoa(cpfCnpjSemPontuacao.length() <= 11 ? "FISICA" : "JURIDICA");
                    return fornecedorRepository.save(novo);
                });
    }

    public Fornecedor buscarPorCnpjCpf(String doc) {
        return fornecedorRepository.findByCpfOuCnpj(doc)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado: " + doc));
    }

    @Transactional
    public Fornecedor salvar(Fornecedor fornecedor) {
        // Verifica duplicidade antes de salvar
        Optional<Fornecedor> existente = fornecedorRepository.findByCpfOuCnpj(fornecedor.getCpfOuCnpj());
        if (existente.isPresent() && !existente.get().getId().equals(fornecedor.getId())) {
            throw new IllegalArgumentException("Já existe um fornecedor cadastrado com este CPF/CNPJ.");
        }
        return fornecedorRepository.save(fornecedor);
    }


}