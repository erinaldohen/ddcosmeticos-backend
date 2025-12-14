package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private NfceService nfceService;          // Para gerar XMLs (Entrada e Baixa)
    @Autowired private TributacaoService tributacaoService; // Para definir NCM e Monofásico

    /**
     * MÉTODO 1: REGISTRAR ENTRADA (COMPRA DE MERCADORIA)
     * --------------------------------------------------
     * Este é o método principal para alimentar o estoque. Ele lida com a complexidade
     * de fornecedores informais (CPF) e calcula o custo médio para a contabilidade.
     */
    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        // 1. Identificação do Usuário (Quem está fazendo a entrada?)
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. Busca do Produto
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado. Cadastre-o antes de dar entrada."));

        // 3. Gestão Inteligente de Fornecedor (CPF ou CNPJ)
        // Se o fornecedor não existir, cria automaticamente para manter histórico.
        Fornecedor fornecedor = null;
        if (dados.getFornecedorCnpj() != null && !dados.getFornecedorCnpj().isBlank()) {
            // Remove formatação (pontos, traços) para salvar limpo no banco
            String documentoLimpo = dados.getFornecedorCnpj().replaceAll("\\D", "");

            fornecedor = fornecedorRepository.findByCpfOuCnpj(documentoLimpo) // Note: Ajustar repositório para buscar por CpfOuCnpj se necessário
                    .orElseGet(() -> {
                        Fornecedor novo = new Fornecedor();
                        novo.setCpfOuCnpj(documentoLimpo);
                        // Define se é Pessoa Física (CPF 11 dígitos) ou Jurídica
                        novo.setTipoPessoa(documentoLimpo.length() > 11 ? "JURIDICA" : "FISICA");
                        novo.setRazaoSocial("Fornecedor Auto-Cadastrado (" + documentoLimpo + ")");
                        novo.setAtivo(true);
                        return fornecedorRepository.save(novo);
                    });
        }

        // 4. Inteligência Tributária (NCM e Monofásico)
        // Se o produto não tem classificação, o sistema infere pela descrição agora.
        tributacaoService.classificarProduto(produto);

        // 5. Cálculo Contábil: Preço Médio Ponderado (PMP)
        // Fórmula: ((EstoqueAtual * CustoAtual) + (QtdNova * CustoNovo)) / (EstoqueTotal)
        BigDecimal qtdAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        BigDecimal qtdNova = dados.getQuantidade();
        BigDecimal custoNovo = dados.getPrecoCusto();

        BigDecimal valorTotalEstoqueAntigo = qtdAtual.multiply(custoMedioAtual);
        BigDecimal valorTotalEntrada = qtdNova.multiply(custoNovo);
        BigDecimal qtdFinal = qtdAtual.add(qtdNova);

        BigDecimal novoPmp = custoNovo; // Default para primeiro lote
        if (qtdFinal.compareTo(BigDecimal.ZERO) > 0) {
            novoPmp = (valorTotalEstoqueAntigo.add(valorTotalEntrada))
                    .divide(qtdFinal, 4, RoundingMode.HALF_UP);
        }

        // 6. Atualização do Produto
        produto.setQuantidadeEmEstoque(qtdFinal);
        produto.setPrecoMedioPonderado(novoPmp);       // Custo Médio (Para Contabilidade)
        produto.setPrecoCustoInicial(custoNovo);       // Último Custo Pago (Para Precificação)
        produto.setPossuiNfEntrada(true);              // A partir de agora, é um produto FISCAL

        produtoRepository.save(produto);

        // 7. Registro no Kardex (Movimento de Estoque)
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setFornecedor(fornecedor);
        mov.setTipoMovimento("ENTRADA_COMPRA");
        mov.setQuantidadeMovimentada(qtdNova);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(custoNovo);

        // Tenta extrair número da nota se fornecido
        if (dados.getNumeroNotaFiscal() != null) {
            try {
                String numeroLimpo = dados.getNumeroNotaFiscal().replaceAll("\\D", "");
                if (!numeroLimpo.isEmpty()) mov.setIdReferencia(Long.parseLong(numeroLimpo));
            } catch (NumberFormatException ignored) {}
        }
        movimentoEstoqueRepository.save(mov);

        // 8. Regularização Fiscal de Entrada (Compra de CPF)
        // Se comprou de Pessoa Física, o sistema gera a NF-e de Entrada (CFOP 1.102)
        String infoFiscal = "Entrada Normal (CNPJ)";
        if (fornecedor != null && "FISICA".equals(fornecedor.getTipoPessoa())) {
            String xmlEntrada = nfceService.gerarXmlNotaEntradaPF(produto, qtdNova, fornecedor);
            infoFiscal = "NOTA DE ENTRADA (CPF) GERADA: " + xmlEntrada;
            // TODO: Futuramente, enviar esse XML para a SEFAZ
        }

        // 9. Auditoria
        Auditoria audit = new Auditoria();
        audit.setTipoEvento("ESTOQUE_ENTRADA");
        audit.setUsuarioResponsavel(usuarioLogado);
        audit.setEntidadeAfetada("Produto: " + produto.getDescricao());
        audit.setIdEntidadeAfetada(produto.getId());
        audit.setMensagem("Entrada: " + qtdNova + " un. Novo PMP: " + novoPmp + ". " + infoFiscal);

        auditoriaRepository.save(audit);
    }

    /**
     * MÉTODO 2: AJUSTE DE INVENTÁRIO (PERDAS, QUEBRAS, FURTOS)
     * --------------------------------------------------------
     * Permite corrigir o estoque manualmente. Se for uma PERDA de um produto
     * que tem lastro fiscal, o sistema gera a Nota de Baixa (CFOP 5.927) para
     * estornar o imposto e evitar problemas de sonegação.
     */
    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dados) {
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();

        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

        BigDecimal estoqueAntigo = produto.getQuantidadeEmEstoque();
        BigDecimal estoqueNovo = dados.getNovaQuantidadeReal();
        BigDecimal diferenca = estoqueNovo.subtract(estoqueAntigo);

        if (diferenca.compareTo(BigDecimal.ZERO) == 0) return; // Nada mudou

        // 1. Atualizar Produto
        produto.setQuantidadeEmEstoque(estoqueNovo);
        produtoRepository.save(produto);

        // 2. Classificar o Movimento
        boolean isPerda = diferenca.compareTo(BigDecimal.ZERO) < 0;
        String tipoMovimento = isPerda ? "AJUSTE_SAIDA_PERDA" : "AJUSTE_ENTRADA_SOBRA";

        // 3. Kardex
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setTipoMovimento(tipoMovimento);
        mov.setQuantidadeMovimentada(diferenca.abs());
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());
        movimentoEstoqueRepository.save(mov);

        // 4. Lógica Fiscal de Baixa (Perda de Produto Fiscal)
        String infoFiscal = "Ajuste Gerencial (Sem impacto fiscal imediato)";

        // REGRA: Se é PERDA e o produto é FISCAL, precisa justificar para a SEFAZ
        if (isPerda && produto.isPossuiNfEntrada()) {
            String xmlBaixa = nfceService.gerarXmlBaixaEstoque(produto, diferenca.abs(), dados.getMotivo());
            infoFiscal = "XML DE BAIXA (CFOP 5.927) GERADO AUTOMATICAMENTE: " + xmlBaixa;
        }

        // 5. Auditoria (Obrigatória com Motivo)
        Auditoria audit = new Auditoria();
        audit.setTipoEvento("INVENTARIO_" + (isPerda ? "PERDA" : "SOBRA"));
        audit.setUsuarioResponsavel(usuarioLogado);
        audit.setEntidadeAfetada("Produto: " + produto.getDescricao());
        audit.setIdEntidadeAfetada(produto.getId());

        // Formata mensagem padronizada para relatórios gerenciais
        String mensagemAudit = String.format("[MOTIVO: %s] Ajuste de %s para %s. %s",
                dados.getMotivo().toUpperCase(),
                estoqueAntigo,
                estoqueNovo,
                infoFiscal);

        audit.setMensagem(mensagemAudit);

        auditoriaRepository.save(audit);
    }
}