package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.LoteProduto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.BeanUtils;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.LoteProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository repository;

    @Autowired
    private MovimentoEstoqueRepository auditoriaRepository;

    @Autowired
    private LoteProdutoRepository loteRepository; // Novo repositório para controlar validade

    // =================================================================================
    // 1. BUSCA INTELIGENTE (Prioridade EAN > Descrição)
    // =================================================================================
    public List<Produto> buscarInteligente(String termo) {
        // Se a busca for vazia, retorna todos os ativos (limite de paginação recomendado no futuro)
        if (termo == null || termo.isBlank()) {
            return repository.findAll();
        }

        // LÓGICA DE OURO: Tenta achar exato pelo EAN primeiro (Prioridade Máxima)
        var produtoPorEan = repository.findByCodigoBarras(termo);
        if (produtoPorEan.isPresent()) {
            return List.of(produtoPorEan.get());
        }

        // Se não achou EAN, busca por parte do nome (ignorando maiúsculas)
        return repository.findByDescricaoContainingIgnoreCase(termo);
    }

    // =================================================================================
    // 2. CADASTRO BLINDADO (Com Verificação de Inativos)
    // =================================================================================
    @Transactional
    public Produto salvar(ProdutoDTO dados) {
        // Validação: Verifica se o EAN já existe no banco INTEIRO (inclusive inativos)
        // Isso evita erro de Constraint Unique no banco de dados.
        var existente = repository.findByEanIrrestrito(dados.codigoBarras());

        if (existente.isPresent()) {
            Produto p = existente.get();
            if (p.isAtivo()) {
                throw new IllegalArgumentException("Já existe um produto ATIVO com este EAN: " + p.getDescricao());
            } else {
                // Inovação: Avisa o front que o produto existe e sugere reativação
                throw new IllegalStateException("Este produto existe mas está INATIVO (ID: " + p.getId() + "). Utilize a função de reativação.");
            }
        }

        // Criação do objeto
        Produto novo = new Produto();
        BeanUtils.copyProperties(dados, novo);

        // Definições Iniciais
        novo.setAtivo(true);
        novo.setPrecoMedioPonderado(dados.precoCusto()); // No início, Médio = Custo Inicial

        // Separação de Estoque Inicial (Assume-se Sem Nota se não informado o contrário no cadastro simples)
        // Idealmente, cadastro simples gera estoque "Não Fiscal" ou zero.
        if (dados.quantidadeEstoque() > 0) {
            novo.setEstoqueNaoFiscal(dados.quantidadeEstoque());
            novo.atualizarSaldoTotal();
        }

        Produto salvo = repository.save(novo);

        // AUDITORIA: Se nasceu com estoque, grava o log inicial
        if (dados.quantidadeEstoque() > 0) {
            registrarAuditoria(
                    salvo, TipoMovimentoEstoque.ENTRADA, "ESTOQUE_INICIAL", "CADASTRO_SISTEMA",
                    dados.quantidadeEstoque(), dados.precoCusto(),
                    0, dados.quantidadeEstoque(), false // false = considera sem nota no cadastro rápido
            );
        }

        return salvo;
    }

    // =================================================================================
    // 3. ALTERAÇÃO CADASTRAL (Protege dados sensíveis)
    // =================================================================================
    @Transactional
    public Produto atualizar(Long id, ProdutoDTO dados) {
        Produto produto = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        // Copia dados do DTO para a Entidade, MAS...
        // IGNORA campos que não podem ser mudados aqui: ID, Estoque (tem rota própria), Preço Médio (cálculo auto)
        BeanUtils.copyProperties(dados, produto, "id", "ativo", "quantidadeEstoque", "estoqueFiscal", "estoqueNaoFiscal", "precoMedio");

        return repository.save(produto);
    }

    // =================================================================================
    // 4. ENTRADA DE ESTOQUE INTELIGENTE (Fiscal, Financeiro e Validade)
    // =================================================================================
    @Transactional
    public void entradaEstoque(String ean, Integer qtdEntrada, BigDecimal custoEntrada,
                               String numeroNota, String lote, LocalDate validade) {

        // Busca por EAN para agilidade
        Produto produto = repository.findByCodigoBarras(ean)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + ean));

        if (qtdEntrada <= 0) throw new IllegalArgumentException("Quantidade deve ser positiva.");

        // --- A. SNAPSHOT ANTES DA MUDANÇA (Para Auditoria) ---
        Integer saldoFisicoAntes = produto.getQuantidadeEmEstoque();
        Integer saldoFiscalAntes = produto.getEstoqueFiscal();
        Integer saldoNaoFiscalAntes = produto.getEstoqueNaoFiscal();
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        // --- B. LÓGICA HÍBRIDA (Fiscal vs Gerencial) ---
        boolean ehEntradaFiscal = (numeroNota != null && !numeroNota.isBlank());

        if (ehEntradaFiscal) {
            produto.setEstoqueFiscal(saldoFiscalAntes + qtdEntrada);
        } else {
            produto.setEstoqueNaoFiscal(saldoNaoFiscalAntes + qtdEntrada);
        }
        produto.atualizarSaldoTotal(); // Recalcula o total físico

        // --- C. CÁLCULO FINANCEIRO (Preço Médio Ponderado) ---
        // Considera o estoque FÍSICO total para valorar o patrimônio
        if (saldoFisicoAntes == 0) {
            produto.setPrecoMedioPonderado(custoEntrada);
        } else {
            // Fórmula: ((EstoqueAtual * CustoMedio) + (QtdEntrada * CustoEntrada)) / NovoTotal
            BigDecimal valorTotalEstoque = custoMedioAtual.multiply(new BigDecimal(saldoFisicoAntes));
            BigDecimal valorTotalEntrada = custoEntrada.multiply(new BigDecimal(qtdEntrada));

            BigDecimal novoTotalFinanceiro = valorTotalEstoque.add(valorTotalEntrada);
            Integer novoTotalFisico = produto.getQuantidadeEmEstoque(); // Já atualizado

            BigDecimal novoPMP = novoTotalFinanceiro.divide(new BigDecimal(novoTotalFisico), 4, RoundingMode.HALF_UP);
            produto.setPrecoMedioPonderado(novoPMP);
        }

        // Atualiza referência de última compra
        produto.setPrecoCusto(custoEntrada);

        // --- D. INTELIGÊNCIA DE REPOSIÇÃO ---
        // Se houver histórico de vendas (vendaMediaDiaria), atualiza sugestão de estoque mínimo
        produto.recalcularEstoqueMinimoSugerido();

        repository.save(produto);

        // --- E. REGISTRO DE LOTE E VALIDADE ---
        if (lote != null && !lote.isBlank() && validade != null) {
            LoteProduto novoLote = new LoteProduto();
            novoLote.setProduto(produto);
            novoLote.setNumeroLote(lote);
            novoLote.setDataValidade(validade);
            novoLote.setQuantidadeAtual(qtdEntrada);
            loteRepository.save(novoLote);
        }

        // --- F. AUDITORIA FINAL ---
        String motivo = ehEntradaFiscal ? "IMPORTACAO_NFE" : "ENTRADA_MANUAL";
        String docRef = ehEntradaFiscal ? numeroNota : "S/N - Ajuste";

        registrarAuditoria(
                produto, TipoMovimentoEstoque.ENTRADA, motivo, docRef,
                qtdEntrada, custoEntrada, saldoFisicoAntes, produto.getQuantidadeEmEstoque(), ehEntradaFiscal
        );
    }

    // =================================================================================
    // 5. GESTÃO DE ESTADO (Ativar/Desativar por EAN)
    // =================================================================================

    @Transactional
    public void inativarPorEan(String ean) {
        Produto p = repository.findByCodigoBarras(ean)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + ean));
        // Soft Delete (graças ao @SQLDelete na Entidade)
        repository.delete(p);
    }

    @Transactional
    public void reativarPorEan(String ean) {
        // Busca Irrestrita (vê inativos)
        Produto p = repository.findByEanIrrestrito(ean)
                .orElseThrow(() -> new RuntimeException("EAN não existe na base de dados."));

        if (p.isAtivo()) {
            throw new IllegalArgumentException("O produto já está ativo.");
        }
        repository.reativarProduto(p.getId());
    }

    // =================================================================================
    // MÉTODOS AUXILIARES
    // =================================================================================
    private void registrarAuditoria(Produto p, TipoMovimentoEstoque tipo, String motivo, String doc,
                                    Integer qtd, BigDecimal valor, Integer saldoAnt, Integer saldoAtu, boolean fiscal) {

        // Chama o método completo passando null no fornecedor
        registrarAuditoriaCompleta(p, tipo, motivo, doc, qtd, valor, saldoAnt, saldoAtu, fiscal, null);
    }

    // Novo Método que aceita Fornecedor (Use este na Importação de XML)
    // Método Corrigido para as linhas 232-238
    private void registrarAuditoriaCompleta(Produto p, TipoMovimentoEstoque tipo, String motivoStr, String doc,
                                            Integer qtd, BigDecimal valor, Integer saldoAnt, Integer saldoAtu,
                                            boolean fiscal, Fornecedor fornecedor) {

        MovimentoEstoque log = new MovimentoEstoque();
        log.setProduto(p);
        log.setFornecedor(fornecedor);
        log.setTipoMovimentoEstoque(tipo);

        // 1. CONVERSÃO DE STRING PARA ENUM (Correção do erro .setMotivo)
        MotivoMovimentacaoDeEstoque motivoEnum;
        if ("IMPORTACAO_NFE".equals(motivoStr)) {
            motivoEnum = MotivoMovimentacaoDeEstoque.COMPRA_FORNECEDOR;
        } else if ("ESTOQUE_INICIAL".equals(motivoStr)) {
            motivoEnum = MotivoMovimentacaoDeEstoque.ESTOQUE_INICIAL;
        } else {
            motivoEnum = MotivoMovimentacaoDeEstoque.AJUSTE_MANUAL;
        }
        log.setMotivoMovimentacaoDeEstoque(motivoEnum);

        // 2. CONVERSÃO DE INTEGER PARA BIGDECIMAL (Correção do erro .setQuantidade)
        log.setQuantidadeMovimentada(new BigDecimal(qtd));

        // 3. RENAME DE CAMPOS (Correção do erro .setValorUnitario)
        log.setCustoMovimentado(valor);

        // Campos de Snapshot (Garantidos pela atualização da Entidade no Passo 2)
        log.setDocumentoReferencia(doc);
        log.setSaldoAnterior(saldoAnt);
        log.setSaldoAtual(saldoAtu);
        log.setMovimentacaoFiscal(fiscal);

        auditoriaRepository.save(log);
    }
}