package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
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

    // --- LEITURA ---

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
        if (termo == null || termo.isBlank()) return produtoRepository.findAll();
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

    // --- ESCRITA ---

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoDTO salvar(ProdutoDTO dto) {
        if (produtoRepository.existsByCodigoBarras(dto.codigoBarras())) {
            throw new IllegalArgumentException("Já existe um produto com este código de barras.");
        }
        Produto produto = new Produto();
        copiarDtoParaEntidade(dto, produto);
        produto.setQuantidadeEmEstoque(0);
        produto.setAtivo(true);
        produtoRepository.save(produto);
        return new ProdutoDTO(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Produto atualizar(Long id, ProdutoDTO dto) {
        Produto produto = buscarPorId(id);
        if (!produto.getCodigoBarras().equals(dto.codigoBarras()) &&
                produtoRepository.existsByCodigoBarras(dto.codigoBarras())) {
            throw new IllegalArgumentException("Código de barras já utilizado.");
        }
        copiarDtoParaEntidade(dto, produto);
        return produtoRepository.save(produto);
    }

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
        Produto p = buscarPorCodigoBarras(ean);
        p.setAtivo(false);
        produtoRepository.save(p);
    }

    @Transactional
    @CacheEvict(value = "produtos", key = "#ean")
    public void reativarPorEan(String ean) {
        Produto p = buscarPorCodigoBarras(ean);
        p.setAtivo(true);
        produtoRepository.save(p);
    }

    @Transactional
    public void atualizarUrlImagem(Long id, String url) {
        Produto p = buscarPorId(id);
        p.setUrlImagem(url);
        produtoRepository.save(p);
    }

    private void copiarDtoParaEntidade(ProdutoDTO dto, Produto produto) {
        produto.setCodigoBarras(dto.codigoBarras());
        produto.setDescricao(dto.descricao());
        produto.setPrecoCusto(dto.precoCusto());
        produto.setPrecoVenda(dto.precoVenda());
        produto.setNcm(dto.ncm());
        produto.setEstoqueMinimo(dto.estoqueMinimo());
        produto.setMonofasico(dto.monofasico() != null ? dto.monofasico() : false);
        produto.setCest(dto.cest());
        produto.setCst(dto.cst());

        // --- MAPEAMENTO DO NOVO CAMPO ---
        if (dto.classificacaoReforma() != null) {
            produto.setClassificacaoReforma(dto.classificacaoReforma());
        } else {
            // Default se o frontend não mandar nada
            produto.setClassificacaoReforma(TipoTributacaoReforma.PADRAO);
        }
    }
}