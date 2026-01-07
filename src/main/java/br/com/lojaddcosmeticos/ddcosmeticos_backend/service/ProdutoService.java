package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
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

import java.util.List;

@Slf4j
@Service
public class ProdutoService {

    @Autowired private ProdutoRepository produtoRepository;

    // --- LEITURA ---

    @Transactional(readOnly = true)
    public Page<Produto> listarComFiltros(String termo, Pageable pageable) {
        if (termo == null || termo.isBlank()) {
            return produtoRepository.findAll(pageable);
        }
        // Busca inteligente (Nome ou EAN) PAGINADA
        return produtoRepository.findByDescricaoContainingIgnoreCaseOrCodigoBarras(termo, termo, pageable);
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
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
        // Certifique-se que o Repository tem o método findByDescricaoContainingIgnoreCaseOrCodigoBarras
        return produtoRepository.findByDescricaoContainingIgnoreCaseOrCodigoBarras(termo, termo);
    }

    public Page<ProdutoDTO> listarTodos(String busca, Pageable pageable) {
        if (busca != null && !busca.isEmpty()) {
            // Certifique-se que o Repository tem o método findByDescricaoContainingIgnoreCaseOrCodigoBarrasContainingIgnoreCase
            return produtoRepository.findByDescricaoContainingIgnoreCaseOrCodigoBarrasContainingIgnoreCase(busca, busca, pageable)
                    .map(ProdutoDTO::new);
        }
        return produtoRepository.findAll(pageable).map(ProdutoDTO::new);
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
        produto.setEstoqueFiscal(0);
        produto.setEstoqueNaoFiscal(0);
        produto.setAtivo(true);

        produto = produtoRepository.save(produto);
        return new ProdutoDTO(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void inativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }


    private void copiarDtoParaEntidade(ProdutoDTO dto, Produto produto) {
        produto.setCodigoBarras(dto.codigoBarras());
        produto.setDescricao(dto.descricao());

        // --- MAPEAMENTO DA MARCA ---
        produto.setMarca(dto.marca());
        produto.setCategoria(dto.categoria());
        produto.setSubcategoria(dto.subcategoria());

        produto.setPrecoCusto(dto.precoCusto());
        produto.setPrecoVenda(dto.precoVenda());
        produto.setNcm(dto.ncm());
        produto.setEstoqueMinimo(dto.estoqueMinimo());
        produto.setMonofasico(dto.monofasico() != null ? dto.monofasico() : false);
        produto.setCest(dto.cest());
        produto.setCst(dto.cst());
        produto.setUrlImagem(dto.urlImagem());

        if (dto.classificacaoReforma() != null) {
            produto.setClassificacaoReforma(dto.classificacaoReforma());
        } else {
            produto.setClassificacaoReforma(TipoTributacaoReforma.PADRAO);
        }
    }
    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void atualizarUrlImagem(Long id, String url) {
        Produto produto = buscarPorId(id);
        produto.setUrlImagem(url);
        produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Produto salvar(Produto produto) {
        if (produtoRepository.existsByCodigoBarras(produto.getCodigoBarras())) {
            // Tenta buscar inclusive os inativos para avisar corretamente
            var existente = produtoRepository.findByEanIrrestrito(produto.getCodigoBarras());
            if(existente.isPresent() && !existente.get().isAtivo()) {
                throw new IllegalArgumentException("Este produto já existe mas está INATIVO. Reative-o ao invés de cadastrar novo.");
            }
            throw new IllegalArgumentException("Já existe um produto com este código de barras.");
        }
        return produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Produto atualizar(Long id, ProdutoDTO dados) {
        Produto produto = buscarPorId(id);

        produto.setDescricao(dados.descricao());
        produto.setPrecoVenda(dados.precoVenda());
        produto.setPrecoCusto(dados.precoCusto());
        produto.setEstoqueMinimo(dados.estoqueMinimo());
        produto.setNcm(dados.ncm());

        // Validação de troca de EAN
        if (!produto.getCodigoBarras().equals(dados.codigoBarras())) {
            if (produtoRepository.existsByCodigoBarras(dados.codigoBarras())) {
                throw new IllegalStateException("Já existe outro produto com este EAN.");
            }
            produto.setCodigoBarras(dados.codigoBarras());
        }

        return produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void inativarPorEan(String ean) {
        var produto = produtoRepository.findByCodigoBarras(ean)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void reativarPorEan(String ean) {
        var produto = produtoRepository.findByEanIrrestrito(ean)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setAtivo(true);
        produtoRepository.save(produto);
    }
}