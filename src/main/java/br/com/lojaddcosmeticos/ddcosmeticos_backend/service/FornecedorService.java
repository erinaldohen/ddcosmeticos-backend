package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto; // <--- Novo Import
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository; // <--- Novo Import
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List; // <--- Novo Import
import java.util.Optional;

@Service
public class FornecedorService {

    @Autowired
    private FornecedorRepository fornecedorRepository;

    @Autowired
    private ProdutoRepository produtoRepository; // <--- Injeção necessária para o BI

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

    // --- MÉTODOS ADICIONADOS PARA A TELA DE ENTRADA E BI ---

    /**
     * Usado para preencher o Dropdown na tela de Entrada de Estoque
     */
    public List<Fornecedor> listarTodos() {
        return fornecedorRepository.findAll();
    }

    /**
     * Inteligência de Compras: Retorna produtos que este fornecedor vende e que estão com estoque baixo
     */
    public List<Produto> obterSugestaoDeCompra(Long fornecedorId) {
        // Chama a query personalizada que criamos no ProdutoRepository
        return produtoRepository.findSugestaoCompraPorFornecedor(fornecedorId);
    }

    public ConsultaCnpjDTO consultarDadosPublicosCnpj(String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");
        String url = "https://brasilapi.com.br/api/cnpj/v1/" + cnpjLimpo;

        RestTemplate restTemplate = new RestTemplate();
        try {
            return restTemplate.getForObject(url, ConsultaCnpjDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao consultar CNPJ: " + e.getMessage());
        }
    }
}