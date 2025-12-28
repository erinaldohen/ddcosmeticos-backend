package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class ProdutoService {

    @Autowired private ProdutoRepository produtoRepository;

    // --- MÉTODOS DE LEITURA ---

    @Transactional(readOnly = true)
    @Cacheable(value = "produtos", key = "#codigoBarras")
    public Produto buscarPorCodigoBarras(String codigoBarras) {
        return produtoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + codigoBarras));
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto ID " + id + " não encontrado"));
    }

    @Transactional(readOnly = true)
    public Page<Produto> listarTodos(Pageable pageable) {
        return produtoRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Produto> buscarInteligente(String termo) {
        if (termo == null || termo.isBlank()) {
            return produtoRepository.findAll();
        }
        return produtoRepository.findByDescricaoContainingIgnoreCaseOrCodigoBarras(termo, termo);
    }

    // --- MÉTODOS DE ESCRITA ---

    @Transactional
    @CacheEvict(value = "produtos", key = "#produto.codigoBarras")
    public Produto salvar(Produto produto) {
        return produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true) // Limpa tudo pois o EAN pode mudar
    public Produto atualizar(Long id, ProdutoDTO dados) {
        Produto produto = buscarPorId(id);

        // Atualiza campos (Mapper manual simples)
        produto.setDescricao(dados.descricao());
        produto.setPrecoVenda(dados.precoVenda());
        produto.setPrecoCusto(dados.precoCusto());
        produto.setEstoqueMinimo(dados.estoqueMinimo());
        produto.setNcm(dados.ncm());

        // Se mudar o código de barras, validar duplicidade
        if (!produto.getCodigoBarras().equals(dados.codigoBarras())) {
            if (produtoRepository.existsByCodigoBarras(dados.codigoBarras())) {
                throw new IllegalStateException("Já existe outro produto com este código de barras.");
            }
            produto.setCodigoBarras(dados.codigoBarras());
        }

        return produtoRepository.save(produto);
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
}