package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    // ==================================================================================
    // LEITURA (Retornando Entidade para compatibilidade com o Controller atual)
    // ==================================================================================

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com ID: " + id));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "produtos", key = "#codigoBarras")
    public Produto buscarPorCodigoBarras(String codigoBarras) {
        return produtoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + codigoBarras));
    }

    @Transactional(readOnly = true)
    public List<Produto> buscarInteligente(String termo) {
        if (termo == null || termo.isBlank()) {
            return produtoRepository.findAll();
        }
        // Busca por nome ou código de barras
        return produtoRepository.findByDescricaoContainingIgnoreCaseOrCodigoBarras(termo, termo);
    }

    @Transactional(readOnly = true)
    public List<ProdutoDTO> listarTodos() {
        return produtoRepository.findAll().stream().map(ProdutoDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public Page<Produto> listarPaginado(Pageable pageable) {
        return produtoRepository.findAll(pageable);
    }

    // ==================================================================================
    // ESCRITA (Recebendo DTO para corrigir o erro da linha 82)
    // ==================================================================================

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoDTO salvar(ProdutoDTO dto) {
        // Validação básica de unicidade
        if (produtoRepository.existsByCodigoBarras(dto.codigoBarras())) {
            throw new IllegalArgumentException("Já existe um produto com este código de barras.");
        }

        Produto produto = new Produto();
        copiarDtoParaEntidade(dto, produto);

        // Valores padrão para novo cadastro
        produto.setQuantidadeEmEstoque(0);
        produto.setAtivo(true);

        produtoRepository.save(produto);
        return new ProdutoDTO(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Produto atualizar(Long id, ProdutoDTO dto) {
        Produto produto = buscarPorId(id);

        // Verifica se mudou o código de barras e se já existe
        if (!produto.getCodigoBarras().equals(dto.codigoBarras()) &&
                produtoRepository.existsByCodigoBarras(dto.codigoBarras())) {
            throw new IllegalArgumentException("Código de barras já utilizado por outro produto.");
        }

        copiarDtoParaEntidade(dto, produto);
        return produtoRepository.save(produto);
    }

    // ==================================================================================
    // GESTÃO DE STATUS E IMAGEM
    // ==================================================================================

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void inativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", key = "#ean")
    public void inativarPorEan(String ean) {
        Produto produto = buscarPorCodigoBarras(ean);
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", key = "#ean")
    public void reativarPorEan(String ean) {
        Produto produto = buscarPorCodigoBarras(ean);
        produto.setAtivo(true);
        produtoRepository.save(produto);
    }

    @Transactional
    public void atualizarUrlImagem(Long id, String url) {
        Produto produto = buscarPorId(id);
        produto.setUrlImagem(url);
        produtoRepository.save(produto);
    }

    // ==================================================================================
    // UTILITÁRIOS
    // ==================================================================================

    private void copiarDtoParaEntidade(ProdutoDTO dto, Produto produto) {
        produto.setCodigoBarras(dto.codigoBarras());
        produto.setDescricao(dto.descricao());
        produto.setPrecoCusto(dto.precoCusto());
        produto.setPrecoVenda(dto.precoVenda());
        produto.setNcm(dto.ncm());
        produto.setEstoqueMinimo(dto.estoqueMinimo());
        // Mapeie outros campos conforme necessário
    }
}