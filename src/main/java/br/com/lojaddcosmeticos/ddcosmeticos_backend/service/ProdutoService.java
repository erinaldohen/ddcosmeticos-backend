package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

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

import java.util.Optional;

@Slf4j
@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    /**
     * Busca produto por código de barras com CACHE.
     * Ideal para o PDV onde a velocidade é crítica.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "produtos", key = "#codigoBarras")
    public Produto buscarPorCodigoBarras(String codigoBarras) {
        log.debug("Buscando produto por código de barras: {}", codigoBarras);
        return produtoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com código: " + codigoBarras));
    }

    /**
     * Busca por ID padrão.
     */
    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com ID: " + id));
    }

    /**
     * Lista todos os produtos com paginação.
     */
    @Transactional(readOnly = true)
    public Page<Produto> listarTodos(Pageable pageable) {
        return produtoRepository.findAll(pageable);
    }

    /**
     * Salva ou Atualiza um produto.
     * IMPORTANTE: Remove o item do cache para evitar dados obsoletos (preço/estoque antigo).
     */
    @Transactional
    @CacheEvict(value = "produtos", key = "#produto.codigoBarras")
    public Produto salvar(Produto produto) {
        log.info("Salvando produto: {}", produto.getDescricao());
        return produtoRepository.save(produto);
    }

    /**
     * Exclusão lógica ou física (dependendo da sua regra).
     * Também limpa o cache.
     */
    @Transactional
    @CacheEvict(value = "produtos", key = "#codigoBarras") // Assume que temos o código ao deletar
    public void excluir(String codigoBarras) {
        Produto produto = buscarPorCodigoBarras(codigoBarras);
        produto.setAtivo(false); // Soft Delete preferencial
        produtoRepository.save(produto);
    }

    // Sobrecarga para deletar por ID se necessário, limpando todo o cache de produtos por segurança
    // ou busque o código de barras antes para limpar específico.
    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void excluirPorId(Long id) {
        if (!produtoRepository.existsById(id)) {
            throw new ResourceNotFoundException("Produto não encontrado para exclusão.");
        }
        produtoRepository.deleteById(id);
    }
}