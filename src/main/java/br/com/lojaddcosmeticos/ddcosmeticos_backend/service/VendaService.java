package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class VendaService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private NfceService nfceService;

    /**
     * Fluxo Principal: Finaliza a venda orquestrando Estoque, Financeiro e Auditoria.
     */
    @Transactional
    public Venda finalizarVenda(VendaRequestDTO dto, String usuarioLogado) {
        log.info("Iniciando finalização de venda - Vendedor: {} | Cliente: {}", usuarioLogado, dto.clienteCpf());

        // 1. Instanciar a Venda com auditoria
        Venda venda = new Venda();
        venda.setDataVenda(LocalDateTime.now());
        venda.setUsuarioVendedor(usuarioLogado);
        venda.setFormaPagamento(dto.formaPagamento());
        venda.setTotalVenda(dto.totalVenda());
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);
        venda.setClienteCpf(dto.clienteCpf());
        venda.setClienteNome(dto.clienteNome());
        venda.setStatusFiscal("PENDENTE");

        // 2. Processar Itens (Baixa de Estoque e CMV)
        for (ItemVendaRequestDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.codigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não cadastrado: " + itemDto.codigoBarras()));

            // Validação de Estoque (Safety Check)
            if (produto.getQuantidadeEmEstoque().compareTo(itemDto.quantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente para: " + produto.getDescricao());
            }

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(itemDto.precoUnitario());
            item.setDescontoItem(itemDto.descontoItem() != null ? itemDto.descontoItem() : BigDecimal.ZERO);

            // SNAPSHOT DO CUSTO (CMV): Essencial para lucro real
            BigDecimal custoAtual = (produto.getPrecoMedioPonderado() != null && produto.getPrecoMedioPonderado().compareTo(BigDecimal.ZERO) > 0)
                    ? produto.getPrecoMedioPonderado() : produto.getPrecoCustoInicial();
            item.setCustoUnitarioHistorico(custoAtual != null ? custoAtual : BigDecimal.ZERO);

            // Totalização do Item
            BigDecimal valorTotalItem = item.getQuantidade().multiply(item.getPrecoUnitario()).subtract(item.getDescontoItem());
            item.setValorTotalItem(valorTotalItem);

            venda.adicionarItem(item);

            // 3. Baixa física no estoque via EstoqueService
            estoqueService.realizarAjusteSaidaVenda(produto, itemDto.quantidade());
        }

        // 4. Salvar Venda e Itens (Cascade)
        Venda vendaSalva = vendaRepository.save(venda);

        // 5. Lançamento Financeiro
        financeiroService.lancarReceitaDeVenda(
                vendaSalva.getId(),
                vendaSalva.getTotalVenda(),
                vendaSalva.getFormaPagamento().name()
        );

        // 6. Integração Fiscal (NFC-e)
        try {
            nfceService.emitirNfce(vendaSalva);
        } catch (Exception e) {
            log.error("Falha na emissão da NFC-e para Venda #{}: {}", vendaSalva.getId(), e.getMessage());
            vendaSalva.setStatusFiscal("ERRO_EMISSAO");
            vendaRepository.save(vendaSalva);
        }

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

    private void cancelarVendaCompleta(Venda venda, String motivo) {
        venda.getItens().forEach(item ->
                estoqueService.estornarEstoqueVenda(item.getProduto(), item.getQuantidade(), "CANCELAMENTO_VENDA")
        );

        financeiroService.cancelarReceitaVenda(venda.getId());

        venda.setCancelada(true);
        venda.setMotivoCancelamento(motivo);
        venda.setStatusFiscal("CANCELADA");
        vendaRepository.save(venda);
        log.info("Cancelamento total da Venda #{} processado.", venda.getId());
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

        financeiroService.ajustarReceitaPorDevolucao(venda.getId(), totalEstornadoBruto);
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