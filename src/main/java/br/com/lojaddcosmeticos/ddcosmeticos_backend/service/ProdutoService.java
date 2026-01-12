package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.audit.CustomRevisionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ProdutoService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CalculadoraFiscalService calculadoraFiscalService;
    @PersistenceContext private EntityManager entityManager;

    // --- MÉTODOS DE CÁLCULO FINANCEIRO ---
    @Transactional
    public void processarEntradaEstoque(Produto produto, Integer quantidadeEntrada, BigDecimal custoEntrada) {
        if (quantidadeEntrada <= 0) return;

        BigDecimal estoqueAtual = new BigDecimal(produto.getQuantidadeEmEstoque());
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado();
        if (custoMedioAtual == null) custoMedioAtual = BigDecimal.ZERO;

        BigDecimal valorTotalAtual = estoqueAtual.multiply(custoMedioAtual);
        BigDecimal valorTotalEntrada = custoEntrada.multiply(new BigDecimal(quantidadeEntrada));

        BigDecimal novoValorTotal = valorTotalAtual.add(valorTotalEntrada);
        BigDecimal novaQuantidade = estoqueAtual.add(new BigDecimal(quantidadeEntrada));

        if (novaQuantidade.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal novoPrecoMedio = novoValorTotal.divide(novaQuantidade, 4, RoundingMode.HALF_UP);
            produto.setPrecoMedioPonderado(novoPrecoMedio);
        } else {
            produto.setPrecoMedioPonderado(custoEntrada);
        }
        produto.setPrecoCusto(custoEntrada);
    }

    // --- LEITURA ---
    @Transactional(readOnly = true)
    public Page<ProdutoListagemDTO> listarResumo(String termo, Pageable pageable) {
        Page<Produto> pagina;
        if (termo == null || termo.isBlank()) {
            pagina = produtoRepository.findAll(pageable);
        } else {
            pagina = produtoRepository.findByDescricaoContainingIgnoreCaseOrCodigoBarras(termo, termo, pageable);
        }

        // Uso do construtor da Classe DTO
        return pagina.map(p -> new ProdutoListagemDTO(
                p.getId(), p.getDescricao(), p.getPrecoVenda(), p.getUrlImagem(),
                p.getQuantidadeEmEstoque(), p.isAtivo(), p.getCodigoBarras(),
                p.getMarca(), p.getNcm()
        ));
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Produto> listarBaixoEstoque() {
        return produtoRepository.findProdutosComBaixoEstoque();
    }

    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistorico(Long id) {
        var reader = AuditReaderFactory.get(entityManager);
        List<Object[]> results = reader.createQuery()
                .forRevisionsOfEntity(Produto.class, false, true)
                .add(AuditEntity.id().eq(id))
                .getResultList();

        List<HistoricoProdutoDTO> historico = new ArrayList<>();
        for (Object[] row : results) {
            Produto pAntigo = (Produto) row[0];
            CustomRevisionEntity info = (CustomRevisionEntity) row[1];
            RevisionType type = (RevisionType) row[2];

            String tipoString = switch (type) {
                case ADD -> "CRIADO";
                case MOD -> "ALTERADO";
                case DEL -> "EXCLUÍDO";
            };
            historico.add(new HistoricoProdutoDTO(
                    info.getId(), new Date(info.getTimestamp()), tipoString,
                    pAntigo.getDescricao(), pAntigo.getPrecoVenda(),
                    pAntigo.getPrecoCusto(), pAntigo.getQuantidadeEmEstoque()
            ));
        }
        historico.sort((a, b) -> b.getDataAlteracao().compareTo(a.getDataAlteracao()));
        return historico;
    }

    // --- ESCRITA ---
    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoDTO salvar(ProdutoDTO dto) {
        if (produtoRepository.existsByCodigoBarras(dto.codigoBarras())) {
            var existente = produtoRepository.findByEanIrrestrito(dto.codigoBarras());
            if(existente.isPresent() && !existente.get().isAtivo()) {
                throw new IllegalArgumentException("Produto existe mas está inativo. Reative-o.");
            }
            throw new IllegalArgumentException("Já existe um produto com este código de barras.");
        }

        Produto produto = new Produto();
        copiarDtoParaEntidade(dto, produto);
        produto.setPrecoMedioPonderado(dto.precoCusto());
        produto.setQuantidadeEmEstoque(0);
        produto.setAtivo(true);
        produto = produtoRepository.save(produto);

        return new ProdutoDTO(produto);
    }

    @Transactional
    public Produto salvar(Produto produto) {
        return produtoRepository.save(produto);
    }

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public Produto atualizar(Long id, ProdutoDTO dados) {
        Produto produto = buscarPorId(id);
        copiarDtoParaEntidade(dados, produto);

        if (!produto.getCodigoBarras().equals(dados.codigoBarras())) {
            if (produtoRepository.existsByCodigoBarras(dados.codigoBarras())) {
                throw new IllegalStateException("Já existe outro produto com este EAN.");
            }
            produto.setCodigoBarras(dados.codigoBarras());
        }
        return produtoRepository.save(produto);
    }

    @Transactional
    public void definirPrecoVenda(Long id, BigDecimal novoPreco) {
        Produto produto = buscarPorId(id);
        produto.setPrecoVenda(novoPreco);
        produtoRepository.save(produto);
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
    public void inativarPorEan(String ean) {
        var produto = produtoRepository.findByCodigoBarras(ean)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setAtivo(false);
        produtoRepository.save(produto);
    }

    // [ESSENCIAL] Reativa usando EAN (chamado pelo Controller)
    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void reativarPorEan(String ean) {
        var produto = produtoRepository.findByEanIrrestrito(ean)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setAtivo(true);
        produtoRepository.save(produto);
    }

    @Transactional
    public String realizarSaneamentoFiscal() {
        List<Produto> todos = produtoRepository.findAll();
        int atualizados = 0;
        for (Produto p : todos) {
            if (calculadoraFiscalService.aplicarRegrasFiscais(p)) {
                atualizados++;
            }
        }
        return String.format("Saneamento concluído. %d produtos atualizados.", atualizados);
    }

    private void copiarDtoParaEntidade(ProdutoDTO dto, Produto produto) {
        produto.setCodigoBarras(dto.codigoBarras());
        produto.setDescricao(dto.descricao());
        produto.setMarca(dto.marca());
        produto.setCategoria(dto.categoria());
        produto.setSubcategoria(dto.subcategoria());
        produto.setUnidade(dto.unidade() != null ? dto.unidade() : "UN");
        produto.setPrecoCusto(dto.precoCusto());
        produto.setPrecoVenda(dto.precoVenda());
        produto.setEstoqueMinimo(dto.estoqueMinimo());
        produto.setUrlImagem(dto.urlImagem());
        produto.setNcm(dto.ncm());
        produto.setCest(dto.cest());
        produto.setCst(dto.cst());
        produto.setMonofasico(Boolean.TRUE.equals(dto.monofasico()));
        produto.setClassificacaoReforma(dto.classificacaoReforma() != null ? dto.classificacaoReforma() : TipoTributacaoReforma.PADRAO);
    }
}