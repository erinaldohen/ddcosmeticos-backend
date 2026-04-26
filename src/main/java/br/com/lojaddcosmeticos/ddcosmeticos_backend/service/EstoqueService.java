package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.RegraNegocioException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private FornecedorService fornecedorService;
    @Autowired private ProdutoFornecedorRepository produtoFornecedorRepository;
    @Autowired private CaixaService caixaService;
    @Autowired private ProdutoService produtoService; // 🔥 Injetamos para usar o Validador GS1

    @Transactional(readOnly = true)
    public List<Produto> gerarSugestaoCompras() { return produtoRepository.findProdutosComBaixoEstoque(); }

    @Transactional(readOnly = true)
    public Page<HistoricoEntradaDTO> listarHistoricoEntradas(Pageable pageable) {
        return movimentoRepository.buscarHistoricoEntradasAgrupado(pageable);
    }

    @Transactional(readOnly = true)
    public List<MovimentoEstoqueDTO> buscarDetalhesNota(String numeroNota) {
        return movimentoRepository.buscarItensDaNota(numeroNota).stream()
                .map(m -> new MovimentoEstoqueDTO(
                        m.getId(), m.getDataMovimento(), m.getTipoMovimentoEstoque(), m.getMotivoMovimentacaoDeEstoque(),
                        m.getProduto().getDescricao() + " (EAN: " + m.getProduto().getCodigoBarras() + ")",
                        m.getQuantidadeMovimentada(), m.getCustoMovimentado(), m.getDocumentoReferencia(), m.getChaveAcesso()
                )).collect(Collectors.toList());
    }

    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitarioNota, Fornecedor fornecedor, String numeroNotaFiscal) {
        BigDecimal qtdEntrada = quantidade;
        BigDecimal novoPrecoMedio = calcularNovoPrecoMedio(produto, qtdEntrada, custoUnitarioNota);
        produto.setPrecoMedioPonderado(novoPrecoMedio);
        produto.setPrecoCusto(custoUnitarioNota);
        int qtdInt = qtdEntrada.intValue();
        produto.setEstoqueFiscal((produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0) + qtdInt);
        produto.setQuantidadeEmEstoque((produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0) + qtdInt);
        produtoRepository.save(produto);
        registrarMovimentacao(produto, qtdEntrada, custoUnitarioNota, TipoMovimentoEstoque.ENTRADA, MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL, "Recebimento Pedido | NF: " + numeroNotaFiscal, numeroNotaFiscal, fornecedor, null);
    }

    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        Produto produto;
        if (dados.getIdProduto() != null) {
            produto = produtoRepository.findById(dados.getIdProduto()).orElseThrow(() -> new ResourceNotFoundException("Produto não existe"));
            if (dados.getFornecedorId() != null && dados.getCodigoNoFornecedor() != null) { salvarVinculoSeNaoExistir(dados.getFornecedorId(), produto, dados.getCodigoNoFornecedor()); }
        } else {
            // 🔥 GATILHO GS1 ANTES DA PESQUISA
            String eanSanitizado = produtoService.auditarECorrigirEanGs1(dados.getCodigoBarras());
            produto = produtoRepository.findByCodigoBarras(eanSanitizado).orElseGet(() -> {
                dados.setCodigoBarras(eanSanitizado);
                Produto novo = criarProdutoAutomatico(dados);
                if (dados.getFornecedorId() != null && dados.getCodigoNoFornecedor() != null) { salvarVinculoSeNaoExistir(dados.getFornecedorId(), novo, dados.getCodigoNoFornecedor()); }
                return novo;
            });
        }

        BigDecimal qtdEntrada = dados.getQuantidade(); BigDecimal custoUnitario = dados.getPrecoCusto();
        BigDecimal novoPrecoMedio = calcularNovoPrecoMedio(produto, qtdEntrada, custoUnitario);
        produto.setPrecoMedioPonderado(novoPrecoMedio); produto.setPrecoCusto(custoUnitario);
        int qtdInt = qtdEntrada.intValue();
        if (dados.getNumeroNotaFiscal() != null && !dados.getNumeroNotaFiscal().isBlank()) { produto.setEstoqueFiscal((produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0) + qtdInt); }
        else { produto.setEstoqueNaoFiscal((produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0) + qtdInt); }
        produto.setQuantidadeEmEstoque((produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0) + qtdInt);

        produtoRepository.save(produto);
        MotivoMovimentacaoDeEstoque motivo = dados.getNumeroNotaFiscal() != null ? MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL : MotivoMovimentacaoDeEstoque.COMPRA_SEM_NOTA_FISCAL;
        Fornecedor fornecedor = null;
        if(dados.getFornecedorId() != null) fornecedor = fornecedorRepository.findById(dados.getFornecedorId()).orElse(null);
        else if(dados.getFornecedorCnpj() != null) fornecedor = fornecedorRepository.findByCnpj(dados.getFornecedorCnpj()).orElse(null);
        registrarMovimentacao(produto, qtdEntrada, custoUnitario, TipoMovimentoEstoque.ENTRADA, motivo, "NF: " + (dados.getNumeroNotaFiscal() != null ? dados.getNumeroNotaFiscal() : "S/N"), dados.getNumeroNotaFiscal(), fornecedor, null);
        gerarFinanceiroBatch(dados, custoUnitario, qtdEntrada.intValue());
    }

    public RetornoImportacaoXmlDTO processarXmlNotaFiscal(MultipartFile arquivo) {
        /* ... Mantido inalterado pois quem invoca este método de parsing legaddo não afeta o GS1 final ... */
        return null; // Apenas para compilar no snippet, seu código real fica aqui.
    }

    @Transactional
    public void processarEntradaEmLote(EntradaEstoqueDTO dto) {
        Fornecedor fornecedor = fornecedorRepository.findById(dto.getFornecedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado"));

        if (dto.getChaveAcesso() != null && !dto.getChaveAcesso().trim().isEmpty()) {
            if (movimentoRepository.existsByChaveAcesso(dto.getChaveAcesso())) {
                throw new RegraNegocioException("Bloqueado: Esta Nota Fiscal (Chave: " + dto.getChaveAcesso() + ") já foi registrada no estoque!");
            }
        } else if (dto.getNumeroDocumento() != null && !dto.getNumeroDocumento().isBlank() && !dto.getNumeroDocumento().equals("S/N")) {
            boolean duplicada = movimentoRepository.existsByDocumentoReferenciaAndFornecedorAndTipoMovimentoEstoque(dto.getNumeroDocumento(), fornecedor, TipoMovimentoEstoque.ENTRADA);
            if (duplicada) throw new RegraNegocioException("Bloqueado: A Nota Fiscal " + dto.getNumeroDocumento() + " deste fornecedor já existe no sistema.");
        }

        for (br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemEntradaDTO itemDto : dto.getItens()) {
            Produto produto;

            if (itemDto.getProdutoId() != null) {
                produto = produtoRepository.findById(itemDto.getProdutoId()).orElseThrow(() -> new ResourceNotFoundException("Produto ID " + itemDto.getProdutoId() + " não encontrado."));
            } else {
                // 🔥 GATILHO GS1 PARA A IMPORTAÇÃO EM MASSA (O CÉREBRO DA NOTA FISCAL) 🔥
                String eanSanitizado = produtoService.auditarECorrigirEanGs1(itemDto.getCodigoBarras());
                Optional<Produto> existente = produtoRepository.findByCodigoBarras(eanSanitizado);

                if (existente.isPresent()) {
                    produto = existente.get();
                } else {
                    produto = new Produto();
                    produto.setDescricao(itemDto.getDescricao() != null ? itemDto.getDescricao().toUpperCase() : "PRODUTO NOVO");
                    produto.setCodigoBarras(eanSanitizado); // Salva o EAN Corrigido on-the-fly!
                    produto.setNcm(itemDto.getNcm());
                    produto.setUnidade(itemDto.getUnidade());
                    produto.setMarca(itemDto.getMarca() != null ? itemDto.getMarca().toUpperCase() : "GENERICA");
                    produto.setCategoria(itemDto.getCategoria() != null ? itemDto.getCategoria().toUpperCase() : "GERAL");
                    produto.setSubcategoria(itemDto.getSubcategoria() != null ? itemDto.getSubcategoria().toUpperCase() : produto.getCategoria());
                    produto.setOrigem(itemDto.getOrigem() != null ? itemDto.getOrigem() : "0");
                    produto.setCst(itemDto.getCst() != null ? itemDto.getCst() : "102");
                    produto.setPrecoCusto(itemDto.getValorUnitario());
                    produto.setPrecoMedioPonderado(itemDto.getValorUnitario());
                    produto.setPrecoVenda(itemDto.getValorUnitario().multiply(new BigDecimal("1.5")));
                    produto.setAtivo(true);
                    produto.setQuantidadeEmEstoque(0);
                    produto.setEstoqueFiscal(0);
                    produto.setEstoqueNaoFiscal(0);

                    produto = produtoRepository.save(produto);
                    salvarVinculoSeNaoExistir(fornecedor.getId(), produto, itemDto.getCodigoBarras());
                }
            }

            BigDecimal qtd = itemDto.getQuantidade(); BigDecimal custo = itemDto.getValorUnitario(); String doc = dto.getNumeroDocumento(); String chaveNfe = dto.getChaveAcesso();
            BigDecimal novoPreco = calcularNovoPrecoMedio(produto, qtd, custo);
            produto.setPrecoCusto(custo); produto.setPrecoMedioPonderado(novoPreco);

            int qtdInt = qtd.intValue();
            if (doc != null && !doc.isBlank() && !doc.equals("S/N")) { produto.setEstoqueFiscal((produto.getEstoqueFiscal() != null ? produto.getEstoqueFiscal() : 0) + qtdInt); }
            else { produto.setEstoqueNaoFiscal((produto.getEstoqueNaoFiscal() != null ? produto.getEstoqueNaoFiscal() : 0) + qtdInt); }
            produto.setQuantidadeEmEstoque((produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0) + qtdInt);

            produtoRepository.save(produto);

            registrarMovimentacao(produto, qtd, custo, TipoMovimentoEstoque.ENTRADA, MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL, "Entrada via Importação", doc, fornecedor, chaveNfe);
        }

        if (dto.getFormaPagamento() != null) { gerarFinanceiroBatch(dto, fornecedor); }
    }

    private Produto criarProdutoAutomatico(EstoqueRequestDTO dados) {
        Produto novo = new Produto();
        // 🔥 GATILHO GS1
        novo.setCodigoBarras(produtoService.auditarECorrigirEanGs1(dados.getCodigoBarras()));
        novo.setDescricao(dados.getDescricao() != null ? dados.getDescricao().toUpperCase() : "PRODUTO NOVO");
        novo.setNcm(dados.getNcm() != null ? dados.getNcm() : "00000000");
        novo.setUnidade(dados.getUnidade() != null ? dados.getUnidade() : "UN");
        novo.setMarca("GENERICA");
        novo.setCategoria("GERAL");
        novo.setOrigem("0");
        novo.setCst("102");
        novo.setPrecoCusto(dados.getPrecoCusto());
        novo.setPrecoMedioPonderado(dados.getPrecoCusto());
        novo.setPrecoVenda(dados.getPrecoCusto().multiply(new BigDecimal("1.5")));
        novo.setAtivo(true);
        novo.setQuantidadeEmEstoque(0);
        novo.setEstoqueFiscal(0);
        novo.setEstoqueNaoFiscal(0);
        return produtoRepository.save(novo);
    }

    private void salvarVinculoSeNaoExistir(Long fornecedorId, Produto produto, String codigoFornecedor) {
        if (fornecedorId == null) return;
        Fornecedor fornecedor = fornecedorRepository.findById(fornecedorId).orElse(null);
        if (fornecedor != null) {
            boolean existe = produtoFornecedorRepository.existsByFornecedorAndCodigoNoFornecedor(fornecedor, codigoFornecedor);
            if (!existe) {
                ProdutoFornecedor vinculo = new ProdutoFornecedor();
                vinculo.setFornecedor(fornecedor); vinculo.setProduto(produto); vinculo.setCodigoNoFornecedor(codigoFornecedor);
                produtoFornecedorRepository.save(vinculo);
            }
        }
    }

    private BigDecimal calcularNovoPrecoMedio(Produto produto, BigDecimal qtdEntrada, BigDecimal custoNovo) {
        BigDecimal estoqueAtual = new BigDecimal(produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : 0);
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;
        if (estoqueAtual.compareTo(BigDecimal.ZERO) <= 0) return custoNovo;
        BigDecimal valorTotal = estoqueAtual.multiply(custoMedioAtual).add(qtdEntrada.multiply(custoNovo));
        BigDecimal novoEstoque = estoqueAtual.add(qtdEntrada);
        return novoEstoque.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : valorTotal.divide(novoEstoque, 4, RoundingMode.HALF_UP);
    }

    private void registrarMovimentacao(Produto p, BigDecimal qtd, BigDecimal custo, TipoMovimentoEstoque tipo, MotivoMovimentacaoDeEstoque motivo, String obs, String ref, Fornecedor f, String chaveAcesso) {
        MovimentoEstoque mov = new MovimentoEstoque(); mov.setProduto(p); mov.setDataMovimento(LocalDateTime.now()); mov.setTipoMovimentoEstoque(tipo); mov.setQuantidadeMovimentada(qtd); mov.setCustoMovimentado(custo); mov.setMotivoMovimentacaoDeEstoque(motivo); mov.setObservacao(obs); mov.setDocumentoReferencia(ref); mov.setFornecedor(f); mov.setSaldoAtual(p.getQuantidadeEmEstoque()); mov.setChaveAcesso(chaveAcesso); movimentoRepository.save(mov);
    }

    private void gerarFinanceiroBatch(EntradaEstoqueDTO dto, Fornecedor fornecedor) { /* ... Lógica mantida ... */ }
    private void gerarFinanceiroBatch(EstoqueRequestDTO dados, BigDecimal custoUnitario, Integer qtd) { /* ... Lógica mantida ... */ }
    private void criarContaPagar(Fornecedor fornecedor, BigDecimal valor, String doc, int parcelaNum, int totalParcelas, LocalDate dataBase, FormaDePagamento formaPagto, int incrementoMes) { /* ... Lógica mantida ... */ }
    @Transactional
    public void registrarSaidaVenda(Produto produto, Integer quantidade) { /* ... Lógica mantida ... */ }
    @Transactional
    public void realizarAjusteManual(AjusteEstoqueDTO dados) { /* ... Lógica mantida ... */ }
}