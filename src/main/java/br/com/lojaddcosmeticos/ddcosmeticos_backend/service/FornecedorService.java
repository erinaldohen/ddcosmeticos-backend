package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FornecedorDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FornecedorService {

    @Autowired
    private FornecedorRepository repository;

    @Autowired
    private ProdutoRepository produtoRepository;

    // --- LISTAGEM PAGINADA ---
    @Transactional(readOnly = true)
    public Page<FornecedorDTO> listar(String termo, Pageable pageable) {
        if (termo != null && !termo.trim().isEmpty()) {
            return repository.buscarPorTermo(termo, pageable).map(this::converterParaDto);
        }
        return repository.findAll(pageable).map(this::converterParaDto);
    }

    // --- LISTAGEM PARA DROPDOWN ---
    @Transactional(readOnly = true)
    public List<FornecedorDTO> listarTodosParaDropdown() {
        return repository.findAll().stream()
                .filter(Fornecedor::isAtivo)
                .map(this::converterParaDto)
                .collect(Collectors.toList());
    }

    // --- BUSCA POR ID ---
    @Transactional(readOnly = true)
    public FornecedorDTO buscarPorId(Long id) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado ID: " + id));
        return converterParaDto(fornecedor);
    }

    // --- SALVAR ---
    @Transactional
    public FornecedorDTO salvar(FornecedorDTO dto) {
        if (repository.existsByCnpj(dto.getCnpj())) {
            throw new ValidationException("Já existe um fornecedor cadastrado com este CNPJ.");
        }
        Fornecedor fornecedor = new Fornecedor();
        copiarDtoParaEntidade(dto, fornecedor);
        fornecedor = repository.save(fornecedor);
        return converterParaDto(fornecedor);
    }

    // --- ATUALIZAR ---
    @Transactional
    public FornecedorDTO atualizar(Long id, FornecedorDTO dto) {
        Fornecedor fornecedor = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado ID: " + id));

        copiarDtoParaEntidade(dto, fornecedor);
        fornecedor = repository.save(fornecedor);
        return converterParaDto(fornecedor);
    }

    // --- EXCLUIR ---
    @Transactional
    public void excluir(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Fornecedor não encontrado");
        }
        repository.deleteById(id);
    }

    // --- PRODUTOS DO FORNECEDOR ---
    @Transactional(readOnly = true)
    public Page<ProdutoListagemDTO> listarProdutosDoFornecedor(Long fornecedorId, Pageable pageable) {
        return produtoRepository.findByFornecedorId(fornecedorId, pageable)
                .map(p -> new ProdutoListagemDTO(
                        p.getId(),
                        p.getDescricao(),
                        p.getPrecoVenda(),
                        p.getUrlImagem(),
                        p.getQuantidadeEmEstoque(),
                        p.isAtivo(),
                        p.getCodigoBarras(),
                        p.getMarca(),
                        p.getNcm()));
    }

    // =================================================================================
    // MÉTODOS DE NEGÓCIO E INTEGRAÇÃO (USADOS PELO ESTOQUE E XML)
    // =================================================================================

    /**
     * Busca um fornecedor pelo CNPJ. Se não existir, tenta criar automaticamente
     * consultando a API externa para garantir dados corretos.
     * (Usado pelo EstoqueService e Importação XML)
     */
    @Transactional
    public Fornecedor buscarOuCriarRapido(String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");

        return repository.findByCnpj(cnpjLimpo).orElseGet(() -> {
            // Se não encontrou no banco, cria um novo
            Fornecedor novo = new Fornecedor();
            novo.setCnpj(cnpjLimpo);
            novo.setAtivo(true);

            // Tenta enriquecer com dados da API Externa
            try {
                ConsultaCnpjDTO dadosApi = consultarDadosCnpj(cnpjLimpo);
                if (dadosApi != null) {
                    novo.setRazaoSocial(dadosApi.getRazaoSocial());
                    novo.setNomeFantasia(dadosApi.getNomeFantasia() != null ? dadosApi.getNomeFantasia() : dadosApi.getRazaoSocial());

                    novo.setCep(dadosApi.getCep());
                    novo.setLogradouro(dadosApi.getLogradouro());
                    novo.setNumero(dadosApi.getNumero());
                    novo.setBairro(dadosApi.getBairro());
                    novo.setCidade(dadosApi.getMunicipio());
                    novo.setUf(dadosApi.getUf());
                    novo.setTelefone(dadosApi.getTelefone());
                    novo.setEmail(dadosApi.getEmail());
                } else {
                    // Fallback se a API falhar
                    novo.setRazaoSocial("FORNECEDOR NOVO " + cnpjLimpo);
                    novo.setNomeFantasia("CADASTRO PENDENTE");
                }
            } catch (Exception e) {
                // Silencia erro da API em criação rápida
                novo.setRazaoSocial("FORNECEDOR NOVO " + cnpjLimpo);
                novo.setNomeFantasia("CADASTRO PENDENTE");
            }

            return repository.save(novo);
        });
    }

    public ConsultaCnpjDTO consultarDadosCnpj(String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("\\D", "");

        if (cnpjLimpo.length() != 14) {
            System.err.println("CNPJ inválido para consulta externa: " + cnpjLimpo);
            return null;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://brasilapi.com.br/api/cnpj/v1/" + cnpjLimpo;
            return restTemplate.getForObject(url, ConsultaCnpjDTO.class);
        } catch (Exception e) {
            System.err.println("Erro ao consultar CNPJ " + cnpjLimpo + " na API externa: " + e.getMessage());
            return null;
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private FornecedorDTO converterParaDto(Fornecedor f) {
        return new FornecedorDTO(
                f.getId(),
                f.getRazaoSocial(),
                f.getNomeFantasia(),
                f.getCnpj(),
                f.getInscricaoEstadual(),
                f.getEmail(),
                f.getTelefone(),
                f.getContato(),
                f.getCep(),
                f.getLogradouro(),
                f.getNumero(),
                // f.getComplemento(), // Removido pois a entidade não tem
                f.getBairro(),
                f.getCidade(),
                f.getUf(),
                f.isAtivo()
        );
    }

    private void copiarDtoParaEntidade(FornecedorDTO dto, Fornecedor entity) {
        entity.setRazaoSocial(dto.getRazaoSocial() != null ? dto.getRazaoSocial().toUpperCase() : "");

        if (dto.getNomeFantasia() != null && !dto.getNomeFantasia().isEmpty()) {
            entity.setNomeFantasia(dto.getNomeFantasia().toUpperCase());
        } else {
            entity.setNomeFantasia(entity.getRazaoSocial());
        }

        entity.setCnpj(dto.getCnpj());
        entity.setInscricaoEstadual(dto.getInscricaoEstadual());
        entity.setTelefone(dto.getTelefone());
        entity.setEmail(dto.getEmail());
        entity.setContato(dto.getContato());

        entity.setCep(dto.getCep());
        entity.setLogradouro(dto.getLogradouro());
        entity.setNumero(dto.getNumero());
        entity.setBairro(dto.getBairro());
        entity.setCidade(dto.getCidade());
        entity.setUf(dto.getUf());

        entity.setAtivo(dto.getAtivo());
    }
}