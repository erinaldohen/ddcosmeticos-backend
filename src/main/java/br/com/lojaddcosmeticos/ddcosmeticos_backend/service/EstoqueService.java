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
import java.util.Optional;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private NfceService nfceService;

    /**
     * REGISTRAR ENTRADA (COMPRA DE MERCADORIA)
     * - Atualiza Estoque
     * - Calcula Preço Médio Ponderado (PMP)
     * - Vincula/Cria Fornecedor
     * - Marca produto como Fiscal
     */
    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();

        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado. Cadastre-o antes de dar entrada."));

        // 1. Lógica de Fornecedor (Find or Create)
        Fornecedor fornecedor = null;
        if (dados.getFornecedorCnpj() != null && !dados.getFornecedorCnpj().isBlank()) {
            String cnpjLimpo = dados.getFornecedorCnpj().replaceAll("\\D", ""); // Remove pontos e traços

            fornecedor = fornecedorRepository.findByCnpj(cnpjLimpo)
                    .orElseGet(() -> {
                        Fornecedor novo = new Fornecedor();
                        novo.setCnpj(cnpjLimpo);
                        novo.setRazaoSocial("Fornecedor (Auto-Cadastrado via Entrada)");
                        novo.setAtivo(true);
                        return fornecedorRepository.save(novo);
                    });
        }

        // 2. Cálculo do Preço Médio Ponderado (PMP)
        // PMP = ((EstoqueAtual * CustoAtual) + (QtdNova * CustoNovo)) / (EstoqueAtual + QtdNova)

        // Garante zero se for null para evitar erro matemático
        BigDecimal qtdAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

        BigDecimal qtdNova = dados.getQuantidade();
        BigDecimal custoNovo = dados.getPrecoCusto();

        BigDecimal valorTotalEstoqueAntigo = qtdAtual.multiply(custoMedioAtual);
        BigDecimal valorTotalEntrada = qtdNova.multiply(custoNovo);
        BigDecimal qtdFinal = qtdAtual.add(qtdNova);

        BigDecimal novoPmp = custoNovo; // Se for o primeiro item, o PMP é o próprio custo
        if (qtdFinal.compareTo(BigDecimal.ZERO) > 0) {
            novoPmp = (valorTotalEstoqueAntigo.add(valorTotalEntrada))
                    .divide(qtdFinal, 4, RoundingMode.HALF_UP);
        }

        // 3. Atualizar Produto
        produto.setQuantidadeEmEstoque(qtdFinal);
        produto.setPrecoMedioPonderado(novoPmp);
        produto.setPrecoCustoInicial(custoNovo); // Atualiza referência de último preço pago
        produto.setPossuiNfEntrada(true); // Entrou com nota, então agora é fiscal!

        produtoRepository.save(produto);

        // 4. Registrar Movimentação (Kardex)
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setFornecedor(fornecedor);
        mov.setTipoMovimento("ENTRADA_COMPRA");
        mov.setQuantidadeMovimentada(qtdNova);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setCustoMovimentado(custoNovo);

        // Extrai apenas números da nota fiscal se vier preenchida
        if (dados.getNumeroNotaFiscal() != null) {
            try {
                String numeroLimpo = dados.getNumeroNotaFiscal().replaceAll("\\D", "");
                if (!numeroLimpo.isEmpty()) {
                    mov.setIdReferencia(Long.parseLong(numeroLimpo));
                }
            } catch (NumberFormatException ignored) {}
        }

        movimentoEstoqueRepository.save(mov);

        // 5. Auditoria
        Auditoria audit = new Auditoria();
        audit.setTipoEvento("ESTOQUE_ENTRADA");
        audit.setUsuarioResponsavel(usuarioLogado);
        audit.setEntidadeAfetada("Produto: " + produto.getDescricao());
        audit.setIdEntidadeAfetada(produto.getId());

        String nomeFornecedor = (fornecedor != null) ? fornecedor.getRazaoSocial() : "N/D";
        audit.setMensagem("Entrada de " + qtdNova + " un. Novo PMP: " + novoPmp + ". Fornecedor: " + nomeFornecedor);

        auditoriaRepository.save(audit);
    }

    /**
     * REALIZAR AJUSTE DE INVENTÁRIO (MANUAL)
     * - Ajusta quantidade para cima ou para baixo
     * - Se for PERDA de produto FISCAL -> Gera XML de Baixa (5.927)
     * - Exige motivo
     */
    @Transactional
    public void realizarAjusteInventario(AjusteEstoqueDTO dados) {
        String usuarioLogado = SecurityContextHolder.getContext().getAuthentication().getName();

        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

        BigDecimal estoqueAntigo = produto.getQuantidadeEmEstoque();
        BigDecimal estoqueNovo = dados.getNovaQuantidadeReal();
        BigDecimal diferenca = estoqueNovo.subtract(estoqueAntigo);

        // Se não houve mudança, retorna
        if (diferenca.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // 1. Atualizar Produto
        produto.setQuantidadeEmEstoque(estoqueNovo);
        produtoRepository.save(produto);

        // 2. Definir Tipo (Perda ou Sobra)
        boolean isPerda = diferenca.compareTo(BigDecimal.ZERO) < 0;
        String tipoMovimento = isPerda ? "AJUSTE_SAIDA_PERDA" : "AJUSTE_ENTRADA_SOBRA";

        // 3. Registrar Movimentação
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(produto);
        mov.setTipoMovimento(tipoMovimento);
        mov.setQuantidadeMovimentada(diferenca.abs()); // Sempre positivo no banco
        mov.setDataMovimento(LocalDateTime.now());
        // No ajuste, usamos o PMP atual como custo da perda/ganho
        mov.setCustoMovimentado(produto.getPrecoMedioPonderado());
        movimentoEstoqueRepository.save(mov);

        // 4. Lógica Fiscal (O Pulo do Gato)
        String infoFiscal = "Item Não Fiscal (Ajuste Gerencial)";

        // REGRA: Se é PERDA e o produto tem origem FISCAL, deve gerar XML de Baixa
        if (isPerda && produto.isPossuiNfEntrada()) {
            // Chama o serviço de Nota Fiscal para gerar o XML CFOP 5.927
            String xmlBaixa = nfceService.gerarXmlBaixaEstoque(produto, diferenca.abs(), dados.getMotivo());
            infoFiscal = "XML DE BAIXA (CFOP 5.927) GERADO: " + xmlBaixa;
        }

        // 5. Auditoria (Com Motivo Obrigatório)
        Auditoria audit = new Auditoria();
        audit.setTipoEvento("INVENTARIO_" + (isPerda ? "PERDA" : "SOBRA"));
        audit.setUsuarioResponsavel(usuarioLogado);
        audit.setEntidadeAfetada("Produto: " + produto.getDescricao());
        audit.setIdEntidadeAfetada(produto.getId());

        // Formatação padronizada para facilitar o Relatório de Motivos depois
        // Ex: [MOTIVO: QUEBRA] Ajuste de 10 para 9. XML GERADO...
        String mensagemAudit = String.format("[MOTIVO: %s] Ajuste de %s para %s. %s",
                dados.getMotivo().toUpperCase(),
                estoqueAntigo,
                estoqueNovo,
                infoFiscal);

        audit.setMensagem(mensagemAudit);

        auditoriaRepository.save(audit);
    }
}