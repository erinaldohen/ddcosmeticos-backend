package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.LoteProduto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.LoteProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository repository;

    @Autowired
    private MovimentoEstoqueRepository auditoriaRepository;

    @Autowired
    private LoteProdutoRepository loteRepository;

    // Lista de NCMs comuns de cosméticos que geralmente são Monofásicos
    private final List<String> NCMS_MONOFASICOS = Arrays.asList(
            "3303", "3304", "3305", "3307", "3401"
    );

    // =================================================================================
    // 1. BUSCA INTELIGENTE
    // =================================================================================
    public List<Produto> buscarInteligente(String termo) {
        if (termo == null || termo.isBlank()) {
            return repository.findAll();
        }
        var produtoPorEan = repository.findByCodigoBarras(termo);
        if (produtoPorEan.isPresent()) {
            return List.of(produtoPorEan.get());
        }
        return repository.findByDescricaoContainingIgnoreCase(termo);
    }

    // =================================================================================
    // 2. CADASTRO BLINDADO (COM INTELIGÊNCIA FISCAL)
    // =================================================================================
    @Transactional
    public Produto salvar(ProdutoDTO dados) {
        var existente = repository.findByEanIrrestrito(dados.codigoBarras());

        if (existente.isPresent()) {
            Produto p = existente.get();
            if (p.isAtivo()) {
                throw new IllegalArgumentException("Já existe um produto ATIVO com este EAN: " + p.getDescricao());
            } else {
                throw new IllegalStateException("Este produto existe mas está INATIVO (ID: " + p.getId() + "). Utilize a função de reativação.");
            }
        }

        Produto novo = new Produto();
        // Copia dados básicos
        BeanUtils.copyProperties(dados, novo, "cst", "monofasico");

        // Definições Iniciais
        novo.setAtivo(true);
        novo.setPrecoMedioPonderado(dados.precoCusto());

        // Estoque Inicial (Gerencial)
        if (dados.quantidadeEstoque() != null && dados.quantidadeEstoque() > 0) {
            novo.setEstoqueNaoFiscal(dados.quantidadeEstoque());
            novo.atualizarSaldoTotal();
        }

        // --- APLICAÇÃO DE REGRAS FISCAIS ---
        // Se o DTO trouxe dados manuais de CST/Monofásico, usa eles. Se não, calcula.
        if (dados.cst() != null && !dados.cst().isBlank()) {
            novo.setCst(dados.cst());
            novo.setMonofasico(dados.monofasico() != null ? dados.monofasico() : false);
        } else {
            aplicarRegrasFiscaisAutomaticas(novo);
        }

        Produto salvo = repository.save(novo);

        if (dados.quantidadeEstoque() != null && dados.quantidadeEstoque() > 0) {
            registrarAuditoria(
                    salvo, TipoMovimentoEstoque.ENTRADA, "ESTOQUE_INICIAL", "CADASTRO_SISTEMA",
                    dados.quantidadeEstoque(), dados.precoCusto(),
                    0, dados.quantidadeEstoque(), false
            );
        }

        return salvo;
    }

    // =================================================================================
    // 3. ALTERAÇÃO CADASTRAL
    // =================================================================================
    @Transactional
    public Produto atualizar(Long id, ProdutoDTO dados) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        // Atualiza campos permitidos (Exceto estoques e IDs)
        produto.setDescricao(dados.descricao().toUpperCase());
        produto.setPrecoVenda(dados.precoVenda());
        produto.setUrlImagem(dados.urlImagem());

        // Se NCM mudou, aplica regra fiscal novamente
        boolean ncmMudou = (dados.ncm() != null && !dados.ncm().equals(produto.getNcm()));

        if (dados.ncm() != null) produto.setNcm(dados.ncm());
        if (dados.cest() != null) produto.setCest(dados.cest());

        if (ncmMudou) {
            aplicarRegrasFiscaisAutomaticas(produto);
        }

        return repository.save(produto);
    }

    // =================================================================================
    // 4. LÓGICA FISCAL AUTOMÁTICA
    // =================================================================================
    private void aplicarRegrasFiscaisAutomaticas(Produto produto) {
        if (produto.getNcm() == null || produto.getNcm().isBlank()) {
            produto.setMonofasico(false);
            produto.setCst("102"); // Simples Nacional padrão
            return;
        }

        String ncmLimpo = produto.getNcm().replaceAll("[^0-9]", "");
        boolean ehMonofasico = NCMS_MONOFASICOS.stream().anyMatch(ncmLimpo::startsWith);

        if (ehMonofasico) {
            produto.setMonofasico(true);
            produto.setCst("060"); // ICMS cobrado anteriormente por ST
        } else {
            produto.setMonofasico(false);
            produto.setCst("102"); // Tributado sem permissão de crédito
        }
    }

    // =================================================================================
    // 5. ENTRADA DE ESTOQUE (MANTIDA IGUAL AO SEU ORIGINAL - ESTÁ ÓTIMA)
    // =================================================================================
    @Transactional
    public void entradaEstoque(String ean, Integer qtdEntrada, BigDecimal custoEntrada,
                               String numeroNota, String lote, LocalDate validade) {
        Produto produto = repository.findByCodigoBarras(ean)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + ean));

        if (qtdEntrada <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");

        Integer saldoFisicoAntes = produto.getQuantidadeEmEstoque();
        Integer saldoFiscalAntes = produto.getEstoqueFiscal();
        Integer saldoNaoFiscalAntes = produto.getEstoqueNaoFiscal();
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        boolean ehEntradaFiscal = (numeroNota != null && !numeroNota.isBlank());

        if (ehEntradaFiscal) {
            produto.setEstoqueFiscal(saldoFiscalAntes + qtdEntrada);
        } else {
            produto.setEstoqueNaoFiscal(saldoNaoFiscalAntes + qtdEntrada);
        }
        produto.atualizarSaldoTotal();

        if (saldoFisicoAntes == 0) {
            produto.setPrecoMedioPonderado(custoEntrada);
        } else {
            BigDecimal valorTotalEstoque = custoMedioAtual.multiply(new BigDecimal(saldoFisicoAntes));
            BigDecimal valorTotalEntrada = custoEntrada.multiply(new BigDecimal(qtdEntrada));
            BigDecimal novoTotalFinanceiro = valorTotalEstoque.add(valorTotalEntrada);
            Integer novoTotalFisico = produto.getQuantidadeEmEstoque();

            BigDecimal novoPMP = novoTotalFinanceiro.divide(new BigDecimal(novoTotalFisico), 4, RoundingMode.HALF_UP);
            produto.setPrecoMedioPonderado(novoPMP);
        }

        produto.setPrecoCusto(custoEntrada);
        produto.recalcularEstoqueMinimoSugerido();
        repository.save(produto);

        if (lote != null && !lote.isBlank() && validade != null) {
            LoteProduto novoLote = new LoteProduto();
            novoLote.setProduto(produto);
            novoLote.setNumeroLote(lote);
            novoLote.setDataValidade(validade);
            novoLote.setQuantidadeAtual(qtdEntrada);
            loteRepository.save(novoLote);
        }

        String motivo = ehEntradaFiscal ? "IMPORTACAO_NFE" : "ENTRADA_MANUAL";
        String docRef = ehEntradaFiscal ? numeroNota : "S/N - Ajuste";

        registrarAuditoria(produto, TipoMovimentoEstoque.ENTRADA, motivo, docRef,
                qtdEntrada, custoEntrada, saldoFisicoAntes, produto.getQuantidadeEmEstoque(), ehEntradaFiscal);
    }

    // =================================================================================
    // 6. GESTÃO DE ESTADO
    // =================================================================================
    @Transactional
    public void inativarPorEan(String ean) {
        Produto p = repository.findByCodigoBarras(ean)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + ean));
        repository.delete(p);
    }

    @Transactional
    public void reativarPorEan(String ean) {
        Produto p = repository.findByEanIrrestrito(ean)
                .orElseThrow(() -> new RuntimeException("EAN não existe na base de dados."));
        if (p.isAtivo()) throw new IllegalArgumentException("O produto já está ativo.");
        repository.reativarProduto(p.getId());
    }

    // =================================================================================
    // MÉTODOS AUXILIARES DE AUDITORIA
    // =================================================================================
    private void registrarAuditoria(Produto p, TipoMovimentoEstoque tipo, String motivo, String doc,
                                    Integer qtd, BigDecimal valor, Integer saldoAnt, Integer saldoAtu, boolean fiscal) {
        registrarAuditoriaCompleta(p, tipo, motivo, doc, qtd, valor, saldoAnt, saldoAtu, fiscal, null);
    }

    private void registrarAuditoriaCompleta(Produto p, TipoMovimentoEstoque tipo, String motivoStr, String doc,
                                            Integer qtd, BigDecimal valor, Integer saldoAnt, Integer saldoAtu,
                                            boolean fiscal, Fornecedor fornecedor) {
        MovimentoEstoque log = new MovimentoEstoque();
        log.setProduto(p);
        log.setFornecedor(fornecedor);
        log.setTipoMovimentoEstoque(tipo);

        MotivoMovimentacaoDeEstoque motivoEnum;
        if ("IMPORTACAO_NFE".equals(motivoStr)) motivoEnum = MotivoMovimentacaoDeEstoque.COMPRA_FORNECEDOR;
        else if ("ESTOQUE_INICIAL".equals(motivoStr)) motivoEnum = MotivoMovimentacaoDeEstoque.ESTOQUE_INICIAL;
        else motivoEnum = MotivoMovimentacaoDeEstoque.AJUSTE_MANUAL;

        log.setMotivoMovimentacaoDeEstoque(motivoEnum);
        log.setQuantidadeMovimentada(new BigDecimal(qtd));
        log.setCustoMovimentado(valor);
        log.setDocumentoReferencia(doc);
        log.setSaldoAnterior(saldoAnt);
        log.setSaldoAtual(saldoAtu);
        log.setMovimentacaoFiscal(fiscal);

        auditoriaRepository.save(log);
    }
}