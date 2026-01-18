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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class FornecedorService {

    @Autowired private FornecedorRepository repository;
    @Autowired private ProdutoRepository produtoRepository;

    // --- LEITURA ---

    @Transactional(readOnly = true)
    public Page<FornecedorDTO> listar(String termo, Pageable pageable) {
        if (termo != null && !termo.trim().isEmpty()) {
            return repository.buscarAtivosPorTermo(termo, pageable).map(this::toDTO);
        }
        return repository.findAll(pageable).map(this::toDTO);
    }

    // Método leve para dropdowns (usado na Entrada de Mercadoria)
    @Transactional(readOnly = true)
    public List<FornecedorDTO> listarTodosParaDropdown() {
        return repository.findAll().stream()
                .filter(Fornecedor::isAtivo)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Fornecedor buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado ID: " + id));
    }

    // --- ESCRITA ---

    public FornecedorDTO salvar(FornecedorDTO dto) {
        if (repository.existsByCnpj(dto.getCnpj())) {
            throw new ValidationException("CNPJ já cadastrado.");
        }

        // CORREÇÃO AUTOMÁTICA DO 9º DÍGITO
        if (dto.getTelefone() != null) {
            dto.setTelefone(normalizarTelefone(dto.getTelefone()));
        }

        Fornecedor entity = new Fornecedor();
        updateFromDTO(entity, dto);
        return toDTO(repository.save(entity));
    }

    public FornecedorDTO atualizar(Long id, FornecedorDTO dto) {
        Fornecedor entity = buscarPorId(id);

        // Verifica CNPJ duplicado em outro registro
        if (!entity.getCnpj().equals(dto.getCnpj()) && repository.existsByCnpj(dto.getCnpj())) {
            throw new ValidationException("CNPJ pertence a outro fornecedor.");
        }

        // CORREÇÃO AUTOMÁTICA DO 9º DÍGITO
        if (dto.getTelefone() != null) {
            dto.setTelefone(normalizarTelefone(dto.getTelefone()));
        }

        updateFromDTO(entity, dto);
        return toDTO(repository.save(entity));
    }

    public void excluir(Long id) {
        Fornecedor f = buscarPorId(id);
        f.setAtivo(false); // Soft Delete
        repository.save(f);
    }

    // --- INTEGRAÇÕES E UTILITÁRIOS ---

    public List<Produto> obterSugestaoDeCompra(Long fornecedorId) {
        // Implementar lógica de BI aqui se necessário
        // Retorna lista vazia por enquanto para evitar erro se não houver lógica
        return List.of();
    }

    /**
     * Usado pelo EstoqueService durante importações.
     * Tenta encontrar um fornecedor pelo nome. Se não achar, cria um registro rápido.
     */
    public Fornecedor buscarOuCriarRapido(String termo) {
        if (termo == null || termo.trim().isEmpty()) {
            return null;
        }

        // 1. Tenta buscar existente
        var existentes = repository.buscarAtivosPorTermo(termo, org.springframework.data.domain.PageRequest.of(0, 1));
        if (existentes.hasContent()) {
            return existentes.getContent().get(0);
        }

        // 2. Se não existir, cria provisório
        Fornecedor novo = new Fornecedor();
        novo.setRazaoSocial(termo.toUpperCase());
        novo.setNomeFantasia(termo.toUpperCase());
        // Gera CNPJ temporário único para passar na constraint unique
        novo.setCnpj("TMP" + System.currentTimeMillis());
        novo.setAtivo(true);
        novo.setCep(""); novo.setLogradouro(""); novo.setNumero("");
        novo.setBairro(""); novo.setCidade(""); novo.setUf("");

        return repository.save(novo);
    }

    // --- CONVERSORES ---

    public FornecedorDTO toDTO(Fornecedor f) {
        FornecedorDTO dto = new FornecedorDTO();
        dto.setId(f.getId());
        dto.setRazaoSocial(f.getRazaoSocial());
        dto.setNomeFantasia(f.getNomeFantasia());
        dto.setCnpj(f.getCnpj());
        dto.setInscricaoEstadual(f.getInscricaoEstadual());
        dto.setEmail(f.getEmail());
        dto.setTelefone(f.getTelefone());
        dto.setContato(f.getContato());
        dto.setCep(f.getCep());
        dto.setLogradouro(f.getLogradouro());
        dto.setNumero(f.getNumero());
        dto.setBairro(f.getBairro());
        dto.setCidade(f.getCidade());
        dto.setUf(f.getUf());
        dto.setAtivo(f.isAtivo());
        return dto;
    }

    private void updateFromDTO(Fornecedor entity, FornecedorDTO dto) {
        entity.setRazaoSocial(dto.getRazaoSocial());
        entity.setNomeFantasia(dto.getNomeFantasia());
        entity.setCnpj(dto.getCnpj());
        entity.setInscricaoEstadual(dto.getInscricaoEstadual());
        entity.setEmail(dto.getEmail());
        entity.setTelefone(dto.getTelefone());
        entity.setContato(dto.getContato());
        entity.setCep(dto.getCep());
        entity.setLogradouro(dto.getLogradouro());
        entity.setNumero(dto.getNumero());
        entity.setBairro(dto.getBairro());
        entity.setCidade(dto.getCidade());
        entity.setUf(dto.getUf());
        if (dto.getAtivo() != null) entity.setAtivo(dto.getAtivo());
    }

    @SuppressWarnings("unchecked")
    public ConsultaCnpjDTO consultarCnpjExterno(String cnpj) {
        String cnpjLimpo = cnpj.replaceAll("[^0-9]", "");
        String url = "https://brasilapi.com.br/api/cnpj/v1/" + cnpjLimpo;
        RestTemplate restTemplate = new RestTemplate();

        try {
            Map<String, Object> resposta = restTemplate.getForObject(url, Map.class);
            if (resposta == null) return null;

            ConsultaCnpjDTO dto = new ConsultaCnpjDTO();

            // Dados Básicos
            dto.setRazaoSocial((String) resposta.get("razao_social"));
            dto.setNomeFantasia((String) resposta.get("nome_fantasia"));
            dto.setCnpj((String) resposta.get("cnpj"));

            // NOVO: Mapeamento de E-mail (Geralmente vem na API)
            dto.setTelefone((String) resposta.get("ddd_telefone_1"));
            // BrasilAPI v1 retorna 'email' em minúsculo
            if (resposta.containsKey("email")) {
                // Define no DTO (você precisará adicionar o campo email no ConsultaCnpjDTO se não tiver)
                // Como o ConsultaCnpjDTO atual não tem o campo email, vamos passar via telefone ou criar o campo
                // Sugiro adicionar 'private String email;' no ConsultaCnpjDTO.java
                // Por enquanto, vou assumir que você adicionará lá, ou retornamos num map.
            }

            // Endereço
            dto.setLogradouro((String) resposta.get("logradouro"));
            dto.setNumero((String) resposta.get("numero"));
            dto.setBairro((String) resposta.get("bairro"));
            dto.setMunicipio((String) resposta.get("municipio"));
            dto.setUf((String) resposta.get("uf"));
            dto.setCep((String) resposta.get("cep"));

            // NOTA SOBRE INSCRIÇÃO ESTADUAL (IE):
            // A BrasilAPI não retorna IE. Isso requer consultas na SEFAZ (Sintegra).
            // Se o JSON tiver um campo 'inscricao_estadual' no futuro, mapear aqui.

            return dto;

        } catch (Exception e) {
            e.printStackTrace();
            throw new ValidationException("Erro ao consultar CNPJ: " + e.getMessage());
        }
    }

    /**
     * Regra de Negócio Inteligente:
     * Se o número tiver DDD + 8 dígitos (Total 10) e começar com 6, 7, 8 ou 9,
     * o sistema entende que é celular e adiciona o 9 na frente automaticamente.
     */
    private String normalizarTelefone(String telefone) {
        // 1. Remove tudo que não é número
        String nums = telefone.replaceAll("[^0-9]", "");

        // 2. Lógica do 9º dígito
        // Se tem 10 dígitos (Ex: 11 98888 7777 sem o 9 fica 11 8888 7777)
        if (nums.length() == 10) {
            char primeiroDigitoNumero = nums.charAt(2); // Pula o DDD (índices 0 e 1)

            // Faixa móvel geralmente começa com 6, 7, 8 ou 9
            if (primeiroDigitoNumero >= '6') {
                // Reconstrói adicionando o 9
                String ddd = nums.substring(0, 2);
                String numero = nums.substring(2);
                return ddd + "9" + numero;
            }
        }

        // Retorna apenas números limpos para salvar no banco padronizado
        return nums;
    }
}