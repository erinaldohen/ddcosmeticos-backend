package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor // Implementação Tier 1: Injeção por construtor automática
public class VendaService {

    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final EstoqueService estoqueService;
    private final FinanceiroService financeiroService;
    private final NfceService nfceService;

    /**
     * Fluxo Principal: Finaliza a venda orquestrando Estoque e Financeiro.
     */
    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        // 1. Captura o Usuário Logado do Contexto de Segurança
        Usuario usuarioLogado = (Usuario) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Processando venda PDV - Operador: {} | Cliente CPF: {}",
                usuarioLogado.getMatricula(), dto.clienteCpf());

        Venda venda = new Venda();
        venda.setDataVenda(LocalDateTime.now());
        venda.setUsuario(usuarioLogado);
        venda.setTotalVenda(dto.totalVenda());
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);
        venda.setClienteCpf(dto.clienteCpf());
        venda.setClienteNome(dto.clienteNome());
        venda.setStatusFiscal(StatusFiscal.PENDENTE);

        // 1. Processar Itens (Estoque e CMV)
        for (ItemVendaRequestDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.codigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não cadastrado: " + itemDto.codigoBarras()));

            validarEstoque(produto, itemDto.quantidade());

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(itemDto.precoUnitario());
            item.setDescontoItem(itemDto.descontoItem() != null ? itemDto.descontoItem() : BigDecimal.ZERO);

            // Snapshot do CMV (Preço Médio) no momento da venda
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado());

            BigDecimal valorTotalItem = item.getQuantidade().multiply(item.getPrecoUnitario()).subtract(item.getDescontoItem());
            item.setValorTotalItem(valorTotalItem);

            venda.adicionarItem(item);
            estoqueService.realizarAjusteSaidaVenda(produto, itemDto.quantidade());
        }

        Venda vendaSalva = vendaRepository.save(venda);

        // 2. Orquestração Financeira (Correção das Linhas 69 e 70)
        for (PagamentoRequestDTO p : dto.pagamentos()) {
            // p.formaPagamento() sendo Enum, o .name() extrai a String ("DINHEIRO", "PIX", etc.)
            financeiroService.lancarReceitaDeVenda(
                    vendaSalva.getId(),
                    p.valor(),
                    p.formaPagamento().name()
            );
        }

        // 3. Integração Fiscal
        emitirNfceSilenciosamente(vendaSalva);

        return vendaSalva;
    }

    /**
     * Módulo de Reversão: Processa Cancelamentos e Devoluções.
     */
    @Transactional
    public void processarEstornoOuCancelamento(EstornoRequestDTO dto) {
        Venda venda = vendaRepository.findByIdComItens(dto.vendaId())
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada."));

        if (venda.isCancelada()) {
            throw new ValidationException("Esta venda já está cancelada.");
        }

        if (dto.itensParaDevolver() == null || dto.itensParaDevolver().isEmpty()) {
            cancelarVendaCompleta(venda, dto.motivo());
        } else {
            processarDevolucaoParcial(venda, dto);
        }
    }

    private void cancelarVendaCompleta(Venda venda, String motivoDoCancelamento) {
        venda.getItens().forEach(item ->
                estoqueService.estornarEstoqueVenda(item.getProduto(), item.getQuantidade(), "CANCELAMENTO_VENDA")
        );

        // Delegação correta: FinanceiroService cuida do seu próprio repositório
        financeiroService.cancelarReceitaVenda(venda.getId());

        venda.setCancelada(true);
        venda.setMotivoDoCancelamento(motivoDoCancelamento);
        venda.setStatusFiscal(StatusFiscal.CANCELADA);
        vendaRepository.save(venda);
    }

    private void processarDevolucaoParcial(Venda venda, EstornoRequestDTO dto) {
        BigDecimal totalEstornadoBruto = BigDecimal.ZERO;

        for (ItemEstornoDTO itemEstorno : dto.itensParaDevolver()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemEstorno.codigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

            estoqueService.estornarEstoqueVenda(produto, itemEstorno.quantidade(), "DEVOLUCAO_PARCIAL");

            BigDecimal precoUnitario = buscarPrecoNoItemVenda(venda, itemEstorno.codigoBarras());
            totalEstornadoBruto = totalEstornadoBruto.add(precoUnitario.multiply(itemEstorno.quantidade()));
        }

        // Delegação para o Financeiro fazer o abatimento
        financeiroService.ajustarReceitaPorDevolucao(venda.getId(), totalEstornadoBruto);
    }

    // --- MÉTODOS AUXILIARES ---

    private void validarEstoque(Produto p, BigDecimal qtd) {
        if (p.getQuantidadeEmEstoque().compareTo(qtd) < 0) {
            throw new ValidationException("Estoque insuficiente para: " + p.getDescricao());
        }
    }

    private void emitirNfceSilenciosamente(Venda venda) {
        try {
            nfceService.emitirNfce(venda);
        } catch (Exception e) {
            log.error("Falha na NFC-e (Venda #{}): {}", venda.getId(), e.getMessage());
            venda.setStatusFiscal(StatusFiscal.ERRO_EMISSAO);
            vendaRepository.save(venda);
        }
    }

    private BigDecimal buscarPrecoNoItemVenda(Venda venda, String codigoBarras) {
        return venda.getItens().stream()
                .filter(i -> i.getProduto().getCodigoBarras().equals(codigoBarras))
                .findFirst()
                .map(ItemVenda::getPrecoUnitario)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }
}