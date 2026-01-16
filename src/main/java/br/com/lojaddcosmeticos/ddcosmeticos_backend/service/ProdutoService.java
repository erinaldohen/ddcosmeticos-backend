package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoListagemDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ProdutoService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private CalculadoraFiscalService calculadoraFiscalService;

    // [REFATORAÇÃO] Injeção do serviço especialista para evitar duplicidade de código Envers
    @Autowired private AuditoriaService auditoriaService;

    // --- MÉTODOS DE CÁLCULO FINANCEIRO ---
    @Transactional
    public void processarEntradaEstoque(Produto produto, Integer quantidadeEntrada, BigDecimal custoEntrada) {
        if (quantidadeEntrada <= 0) return;

        BigDecimal estoqueAtual = new BigDecimal(produto.getQuantidadeEmEstoque());
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

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

    // [REFATORAÇÃO] Método simplificado delegando para AuditoriaService
    @Transactional(readOnly = true)
    public List<HistoricoProdutoDTO> buscarHistorico(Long id) {
        // Delega a responsabilidade para o serviço correto, removendo duplicidade de lógica do Hibernate Envers
        return auditoriaService.buscarHistoricoDoProduto(id);
    }

    // --- ESCRITA ---
    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public ProdutoDTO salvar(ProdutoDTO dto) {
        if (produtoRepository.existsByCodigoBarras(dto.codigoBarras())) {
            var existente = produtoRepository.findByEanIrrestrito(dto.codigoBarras());
            if(existente.isPresent() && !existente.get().isAtivo()) {
                throw new ValidationException("Produto existe mas está inativo. Reative-o.");
            }
            throw new ValidationException("Já existe um produto com este código de barras.");
        }

        Produto produto = new Produto();
        copiarDtoParaEntidade(dto, produto);

        // Inicializa preço médio com o custo
        BigDecimal custoInicial = dto.precoCusto() != null ? dto.precoCusto() : BigDecimal.ZERO;
        produto.setPrecoMedioPonderado(custoInicial);

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

        if (dados.codigoBarras() != null && !dados.codigoBarras().equals(produto.getCodigoBarras())) {
            if (produtoRepository.existsByCodigoBarras(dados.codigoBarras())) {
                throw new ValidationException("Já existe outro produto com este EAN.");
            }
        }

        copiarDtoParaEntidade(dados, produto);
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

    @Transactional
    @CacheEvict(value = "produtos", allEntries = true)
    public void reativarPorEan(String ean) {
        var produto = produtoRepository.findByEanIrrestrito(ean)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        produto.setAtivo(true);
        produtoRepository.save(produto);
    }

    @Transactional
        public ResponseEntity<Map<String, Object>> realizarSaneamentoFiscal() {
            List<Produto> produtos = produtoRepository.findAll();
            int total = produtos.size();
            int atualizados = 0;

            log.info("⚡ Iniciando Saneamento Fiscal em {} produtos...", total);

            for (Produto p : produtos) {
                // APLICA A INTELIGÊNCIA: Descrição "Shampoo" -> NCM 33051000 + CST 04
                boolean alterou = calculadoraFiscalService.aplicarRegrasFiscais(p);

                if (alterou) {
                    produtoRepository.save(p);
                    atualizados++;
                }
            }

            log.info("✅ Saneamento concluído. {} produtos corrigidos.", atualizados);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Saneamento concluído com sucesso!");
            response.put("totalProdutos", total);
            response.put("produtosCorrigidos", atualizados);

            return ResponseEntity.ok(response);
        }

    private void copiarDtoParaEntidade(ProdutoDTO dto, Produto produto) {
        // Campos de Identificação e Básicos
        produto.setCodigoBarras(dto.codigoBarras());
        produto.setDescricao(dto.descricao());
        produto.setMarca(dto.marca());
        produto.setCategoria(dto.categoria());
        produto.setSubcategoria(dto.subcategoria());
        produto.setUnidade(dto.unidade() != null ? dto.unidade() : "UN");

        // Dados Fiscais
        produto.setNcm(dto.ncm());
        produto.setCest(dto.cest());
        produto.setCst(dto.cst());
        produto.setOrigem(dto.origem() != null ? dto.origem() : "0");
        produto.setMonofasico(Boolean.TRUE.equals(dto.monofasico()));
        produto.setClassificacaoReforma(dto.classificacaoReforma() != null ? dto.classificacaoReforma() : TipoTributacaoReforma.PADRAO);
        produto.setImpostoSeletivo(Boolean.TRUE.equals(dto.impostoSeletivo()));

        // Financeiro e Estoque
        produto.setPrecoCusto(dto.precoCusto());
        produto.setPrecoVenda(dto.precoVenda());
        produto.setEstoqueMinimo(dto.estoqueMinimo());
        produto.setDiasParaReposicao(dto.diasParaReposicao());
        produto.setUrlImagem(dto.urlImagem());
    }
}