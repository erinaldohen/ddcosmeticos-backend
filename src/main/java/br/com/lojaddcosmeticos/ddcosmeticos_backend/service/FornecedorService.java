package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FornecedorDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class FornecedorService {

    @Autowired private FornecedorRepository repository;
    @Autowired private ProdutoRepository produtoRepository; // Necessário para sugestão de compras

    // --- CRUD PRINCIPAL (MANTIDO) ---

    @Transactional(readOnly = true)
    public Page<FornecedorDTO> listar(String termo, Pageable pageable) {
        Page<Fornecedor> page;
        if (termo == null || termo.isBlank()) {
            page = repository.findAll(pageable);
        } else {
            page = repository.buscarAtivosPorTermo(termo, pageable);
        }
        return page.map(this::toDTO);
    }

    public FornecedorDTO salvar(FornecedorDTO dto) {
        String cnpjLimpo = limparCnpj(dto.cnpj());
        if (repository.existsByCnpj(cnpjLimpo)) {
            throw new ValidationException("Já existe um fornecedor com este CNPJ.");
        }
        Fornecedor f = new Fornecedor();
        copiarDtoParaEntidade(dto, f);
        f.setCnpj(cnpjLimpo);
        return toDTO(repository.save(f));
    }

    public FornecedorDTO atualizar(Long id, FornecedorDTO dto) {
        Fornecedor f = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado"));

        String cnpjLimpo = limparCnpj(dto.cnpj());
        // Verifica duplicidade apenas se o CNPJ mudou
        if (!f.getCnpj().equals(cnpjLimpo) && repository.existsByCnpj(cnpjLimpo)) {
            throw new ValidationException("CNPJ já pertence a outro fornecedor.");
        }

        copiarDtoParaEntidade(dto, f);
        f.setCnpj(cnpjLimpo);
        return toDTO(repository.save(f));
    }

    public void excluir(Long id) {
        Fornecedor f = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado"));
        f.setAtivo(false); // Soft Delete
        repository.save(f);
    }

    public Fornecedor buscarPorId(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado"));
    }

    // --- NOVOS MÉTODOS (INTEGRAÇÃO E REGRAS DE NEGÓCIO) ---

    /**
     * Lista todos os fornecedores ativos sem paginação.
     * Usado para popular combobox/selects em outras telas (ex: Entrada de Estoque).
     */
    @Transactional(readOnly = true)
    public List<FornecedorDTO> listarTodosAtivos() {
        return repository.findAll().stream()
                .filter(Fornecedor::isAtivo)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca por CNPJ retornando Optional (usado para validações no controller).
     */
    @Transactional(readOnly = true)
    public Optional<FornecedorDTO> buscarPorCnpj(String cnpj) {
        return repository.findByCnpj(limparCnpj(cnpj)).map(this::toDTO);
    }

    /**
     * INTEGRAÇÃO BRASIL API:
     * Consulta dados públicos na Receita Federal para auto-preencher o cadastro.
     */
    public ConsultaCnpjDTO consultarDadosPublicosCnpj(String cnpj) {
        String cnpjLimpo = limparCnpj(cnpj);
        String url = "https://brasilapi.com.br/api/cnpj/v1/" + cnpjLimpo;

        try {
            RestTemplate restTemplate = new RestTemplate();
            return restTemplate.getForObject(url, ConsultaCnpjDTO.class);
        } catch (Exception e) {
            // Em caso de erro (CNPJ inválido ou API fora), lançamos exceção amigável
            throw new ValidationException("Não foi possível consultar o CNPJ na Receita Federal (BrasilAPI).");
        }
    }

    /**
     * SUGESTÃO DE COMPRA (INTELIGÊNCIA):
     * Analisa o nome do fornecedor e busca produtos dessa marca que estão com estoque baixo.
     */
    @Transactional(readOnly = true)
    public List<Produto> obterSugestaoDeCompra(Long fornecedorId) {
        Fornecedor f = repository.findById(fornecedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado"));

        // Estratégia simples: Pega a primeira palavra do Nome Fantasia (Ex: "Vult" de "Vult Cosméticos")
        // Idealmente, teríamos uma tabela de vínculo Produto x Fornecedor, mas isso resolve por hora.
        String termoMarca = f.getNomeFantasia().split(" ")[0];

        List<Produto> produtosBaixoEstoque = produtoRepository.findProdutosComBaixoEstoque();

        // Filtra os produtos que contenham o nome da marca/fornecedor
        return produtosBaixoEstoque.stream()
                .filter(p -> p.getMarca() != null &&
                        p.getMarca().toUpperCase().contains(termoMarca.toUpperCase()))
                .collect(Collectors.toList());
    }

    // --- MÉTODOS AUXILIARES ---

    private String limparCnpj(String cnpj) {
        return cnpj != null ? cnpj.replaceAll("[^0-9]", "") : "";
    }

    private FornecedorDTO toDTO(Fornecedor f) {
        return new FornecedorDTO(
                f.getId(), f.getRazaoSocial(), f.getNomeFantasia(), f.getCnpj(),
                f.getInscricaoEstadual(), f.getEmail(), f.getTelefone(), f.getContato(),
                f.getCep(), f.getLogradouro(), f.getNumero(), f.getBairro(), f.getCidade(), f.getUf(),
                f.isAtivo()
        );
    }

    private void copiarDtoParaEntidade(FornecedorDTO dto, Fornecedor f) {
        f.setRazaoSocial(dto.razaoSocial());
        f.setNomeFantasia(dto.nomeFantasia());
        f.setInscricaoEstadual(dto.inscricaoEstadual());
        f.setEmail(dto.email());
        f.setTelefone(dto.telefone());
        f.setContato(dto.contato());
        f.setCep(dto.cep());
        f.setLogradouro(dto.logradouro());
        f.setNumero(dto.numero());
        f.setBairro(dto.bairro());
        f.setCidade(dto.cidade());
        f.setUf(dto.uf());
        f.setAtivo(dto.ativo());
    }

    /**
     * Método auxiliar usado pelo EstoqueService durante importações.
     * Tenta encontrar um fornecedor pelo nome. Se não achar, cria um registro rápido.
     */
    @Transactional
    public Fornecedor buscarOuCriarRapido(String termo) {
        if (termo == null || termo.trim().isEmpty()) {
            return null; // Ou retorne um Fornecedor "Não Identificado" padrão
        }

        // 1. Tenta buscar existente usando a busca que já criamos
        // Usamos paginação de 1 para pegar o mais relevante
        var existentes = repository.buscarAtivosPorTermo(termo, org.springframework.data.domain.PageRequest.of(0, 1));

        if (existentes.hasContent()) {
            return existentes.getContent().get(0);
        }

        // 2. Se não existir, cria um cadastro provisório
        Fornecedor novo = new Fornecedor();
        novo.setRazaoSocial(termo.toUpperCase());
        novo.setNomeFantasia(termo.toUpperCase());

        // GERA UM CNPJ TEMPORÁRIO ÚNICO para passar na restrição do banco de dados (Unique/Not Null)
        // O usuário deverá corrigir isso depois na tela de Fornecedores
        novo.setCnpj("TMP" + System.currentTimeMillis());

        novo.setAtivo(true);

        // Preenche endereços vazios para não dar NullPointer se for obrigatório
        novo.setCep("");
        novo.setLogradouro("");
        novo.setNumero("");
        novo.setBairro("");
        novo.setCidade("");
        novo.setUf("");

        return repository.save(novo);
    }
}