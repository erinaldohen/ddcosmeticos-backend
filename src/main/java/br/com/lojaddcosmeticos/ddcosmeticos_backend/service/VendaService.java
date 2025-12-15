package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Service
public class VendaService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private FinanceiroService financeiroService; // <--- A INTEGRAÇÃO

    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        Venda venda = new Venda();
        venda.setDataVenda(LocalDateTime.now());
        venda.setFormaPagamento(dto.getFormaPagamento()); // Ajuste na Entidade Venda se necessário
        venda.setItens(new ArrayList<>());

        BigDecimal totalVenda = BigDecimal.ZERO;

        // 1. Processa Itens e Baixa Estoque
        for (ItemVendaDTO itemDto : dto.getItens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDto.getCodigoBarras()));

            if (produto.getQuantidadeEmEstoque().compareTo(itemDto.getQuantidade()) < 0) {
                throw new IllegalStateException("Estoque insuficiente para: " + produto.getDescricao());
            }

            // Baixa de Estoque
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(itemDto.getQuantidade()));
            produtoRepository.save(produto);

            ItemVenda item = new ItemVenda();
            item.setVenda(venda);
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());

            // Calcula totais
            BigDecimal totalItem = produto.getPrecoVenda().multiply(itemDto.getQuantidade());
            item.setValorTotalItem(totalItem);

            // Custo (Para relatório de lucro futuro)
            item.setCustoUnitario(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);
            item.setCustoTotal(item.getCustoUnitario().multiply(itemDto.getQuantidade()));

            venda.getItens().add(item);
            totalVenda = totalVenda.add(totalItem);
        }

        venda.setTotalVenda(totalVenda);

        // Salva a Venda
        Venda vendaSalva = vendaRepository.save(venda);

        // 2. INTEGRAÇÃO FINANCEIRA (O Dinheiro Entrando)
        financeiroService.lancarReceitaDeVenda(
                vendaSalva.getId(),
                vendaSalva.getTotalVenda(),
                dto.getFormaPagamento(),
                dto.getQuantidadeParcelas()
        );

        return vendaSalva;
    }
}