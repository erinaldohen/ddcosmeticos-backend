package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class VendaService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EstoqueService estoqueService;
    @Autowired private FinanceiroService financeiroService;
    @Autowired private NfceService nfceService;

    /**
     * Realiza o processamento completo da venda.
     * Orquestra: Validação -> Snapshot Fiscal/Custo -> Estoque -> Financeiro -> NFC-e.
     */
    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        log.info("Processando venda PDV - Cliente CPF: {}", dto.getCpfCliente() != null ? dto.getCpfCliente() : "N/I");

        Venda venda = new Venda();
        venda.setClienteCpf(dto.getCpfCliente());
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusFiscal("PENDENTE");

        BigDecimal totalVenda = BigDecimal.ZERO;

        for (ItemVendaDTO itemDto : dto.getItens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não cadastrado: " + itemDto.getCodigoBarras()));

            // 1. Validação de Disponibilidade
            if (produto.getQuantidadeEmEstoque().compareTo(itemDto.getQuantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente para: " + produto.getDescricao());
            }

            // 2. Snapshot para Cálculo de Lucro Real e CMV
            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());

            // Travamos o custo médio atual no item da venda.
            // Essencial para o relatório de lucro não mudar se o custo do produto subir amanhã.
            item.setCustoUnitario(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);

            BigDecimal valorItem = item.getPrecoUnitario().multiply(item.getQuantidade());
            item.setValorTotalItem(valorItem);
            item.setCustoTotal(item.getCustoUnitario().multiply(item.getQuantidade()));

            venda.adicionarItem(item);
            totalVenda = totalVenda.add(valorItem);
        }

        venda.setTotalVenda(totalVenda);
        venda.setDescontoTotal(BigDecimal.ZERO); // Expansível para lógica de cupons

        // 3. Persistência da Transação
        Venda vendaSalva = vendaRepository.save(venda);

        // 4. Integração com Módulos Satélites
        executarFluxosOperacionais(vendaSalva, dto);

        // 5. Tentativa de Emissão de Cupom Fiscal (NFC-e)
        try {
            nfceService.emitirNfce(vendaSalva);
        } catch (Exception e) {
            log.warn("SEFAZ Indisponível para Venda #{} - Operando em Contingência.", vendaSalva.getId());
            vendaSalva.setStatusFiscal("CONTINGENCIA");
            vendaRepository.save(vendaSalva);
        }

        return vendaSalva;
    }

    /**
     * Garante a baixa física no estoque e o lançamento no Contas a Receber.
     */
    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        // Baixa de Estoque Automática
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setTipoMovimento("SAIDA_VENDA");
            estoqueService.realizarAjusteInventario(ajuste);
        });

        // Lançamento Financeiro (Cálculo de D+1 para cartões integrado)
        financeiroService.lancarReceitaDeVenda(
                venda.getId(),
                venda.getTotalVenda(),
                dto.getFormaPagamento(),
                dto.getQuantidadeParcelas()
        );
    }

    /**
     * Busca os detalhes de uma venda incluindo seus itens para fins fiscais ou relatórios.
     */
    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada para processamento fiscal."));
    }
}