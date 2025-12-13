package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
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
    @Autowired private NfceService nfceService;

    // --- NOVO MÉTODO: ENTRADA DE MERCADORIA (COMPRA) ---
    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();

        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado. Cadastre-o antes de dar entrada."));

        // 1. Cálculo do Preço Médio Ponderado (PMP) - Contabilidade
        BigDecimal qtdAtual = produto.getQuantidadeEmEstoque();
        BigDecimal custoAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        BigDecimal qtdNova = dados.getQuantidade();
        BigDecimal custoNovo = dados.getPrecoCusto();

        BigDecimal valorTotalEstoqueAntigo = qtdAtual.multiply(custoAtual);
        BigDecimal valorTotalEntrada = qtdNova.multiply(custoNovo);

        BigDecimal qtdFinal = qtdAtual.add(qtdNova);

        // Evita divisão por zero se estoque for negativo ou zero
        BigDecimal novoPmp = custoNovo;
        if (qtdFinal.compareTo(BigDecimal.ZERO) > 0) {
            novoPmp = (valorTotalEstoqueAntigo.add(valorTotalEntrada))
                    .divide(qtdFinal, 4, RoundingMode.HALF_UP);
        }

        // 2. Atualiza Produto
        produto.setQuantidadeEmEstoque(qtdFinal);
        produto.setPrecoMedioPonderado(novoPmp);
        produto.setPrecoCustoInicial(custoNovo); // Atualiza último custo pago
        produto.setPossuiNfEntrada(true); // Se entrou com Nota, agora é Fiscal!

        produtoRepository.save(produto);

        // 3. Kardex
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setTipoMovimento("ENTRADA_COMPRA");
        mov.setQuantidadeMovimentada(qtdNova);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(custoNovo);
        mov.setIdReferencia(dados.getNumeroNotaFiscal() != null ? Long.parseLong(dados.getNumeroNotaFiscal().replaceAll("\\D", "")) : null);
        movimentoEstoqueRepository.save(mov);

        // 4. Auditoria
        Auditoria audit = new Auditoria();
        audit.setTipoEvento("ESTOQUE_ENTRADA");
        audit.setUsuarioResponsavel(usuarioLogado);
        audit.setEntidadeAfetada("Produto: " + produto.getDescricao());
        audit.setIdEntidadeAfetada(produto.getId());
        audit.setMensagem("Entrada de " + qtdNova + " un. Novo PMP: " + novoPmp + ". NF: " + dados.getNumeroNotaFiscal());
        auditoriaRepository.save(audit);
    }

    // --- MÉTODO EXISTENTE: AJUSTE DE INVENTÁRIO (MANUAL) ---
    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dados) {
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();

        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

        BigDecimal estoqueAntigo = produto.getQuantidadeEmEstoque();
        BigDecimal estoqueNovo = dados.getNovaQuantidadeReal();
        BigDecimal diferenca = estoqueNovo.subtract(estoqueAntigo);

        if (diferenca.compareTo(BigDecimal.ZERO) == 0) return;

        // Atualiza Estoque
        produto.setQuantidadeEmEstoque(estoqueNovo);
        produtoRepository.save(produto);

        boolean isPerda = diferenca.compareTo(BigDecimal.ZERO) < 0;
        String tipoMovimento = isPerda ? "AJUSTE_SAIDA_PERDA" : "AJUSTE_ENTRADA_SOBRA";

        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setTipoMovimento(tipoMovimento);
        mov.setQuantidadeMovimentada(diferenca.abs());
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());
        movimentoEstoqueRepository.save(mov);

        String infoFiscal = "Item Não Fiscal (Ajuste Gerencial)";
        if (isPerda && produto.isPossuiNfEntrada()) {
            String xmlBaixa = nfceService.gerarXmlBaixaEstoque(produto, diferenca.abs(), dados.getMotivo());
            infoFiscal = "XML DE BAIXA (CFOP 5.927) GERADO: " + xmlBaixa;
        }

        Auditoria audit = new Auditoria();
        audit.setTipoEvento("INVENTARIO_" + (isPerda ? "PERDA" : "SOBRA"));
        audit.setUsuarioResponsavel(usuarioLogado);
        audit.setEntidadeAfetada("Produto: " + produto.getDescricao());
        audit.setIdEntidadeAfetada(produto.getId());
        String mensagemAudit = String.format("[MOTIVO: %s] Ajuste de %s para %s. %s",
                dados.getMotivo().toUpperCase(), estoqueAntigo, estoqueNovo, infoFiscal);
        audit.setMensagem(mensagemAudit);
        auditoriaRepository.save(audit);
    }
}