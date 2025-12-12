package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class VendaService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;

    // Injetaremos o NfceService depois para a chamada automática, se necessário.

    @Transactional
    public VendaResponseDTO registrarVenda(VendaRequestDTO dadosVenda) {
        String matriculaOperador = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario operador = usuarioRepository.findByMatricula(matriculaOperador)
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado."));

        Venda venda = new Venda();
        venda.setOperador(operador);
        venda.setDataVenda(LocalDateTime.now());
        venda.setDesconto(dadosVenda.getDesconto() != null ? dadosVenda.getDesconto() : BigDecimal.ZERO);

        // Novo controle de Status
        // Se houver auditoria de estoque negativo, pode ficar como PENDENTE_ANALISE
        String statusFiscalInicial = "PRONTA_PARA_EMISSAO";

        List<ItemVenda> itensParaSalvar = new ArrayList<>();
        List<String> alertasParaCaixa = new ArrayList<>();
        BigDecimal somaTotal = BigDecimal.ZERO;

        for (ItemVendaDTO itemDTO : dadosVenda.getItens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDTO.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDTO.getCodigoBarras()));

            BigDecimal quantidadeVendida = itemDTO.getQuantidade();
            BigDecimal estoqueAtual = produto.getQuantidadeEmEstoque();
            BigDecimal estoqueFuturo = estoqueAtual.subtract(quantidadeVendida);

            // --- REGRA DE NEGÓCIO: Permite Negativo mas Audita ---
            if (estoqueFuturo.compareTo(BigDecimal.ZERO) < 0) {
                // 1. Gera Alerta para o Front-end
                alertasParaCaixa.add("ALERTA: Produto '" + produto.getDescricao() + "' ficou com ESTOQUE NEGATIVO (" + estoqueFuturo + ").");

                // 2. Grava Auditoria Permanente
                Auditoria auditoria = new Auditoria();
                auditoria.setTipoEvento("ESTOQUE_NEGATIVO");
                auditoria.setUsuarioResponsavel(matriculaOperador);
                auditoria.setEntidadeAfetada("Produto");
                auditoria.setIdEntidadeAfetada(produto.getId());
                auditoria.setMensagem("Venda realizada com estoque insuficiente. Estoque Anterior: " + estoqueAtual + ", Venda: " + quantidadeVendida + ", Novo: " + estoqueFuturo);
                auditoriaRepository.save(auditoria);

                // 3. Define pendência se o produto for fiscal
                if (produto.isPossuiNfEntrada()) {
                    statusFiscalInicial = "PENDENTE_ANALISE_GERENTE";
                    alertasParaCaixa.add("NOTA: A emissão fiscal deste item entrou em análise devido à inconsistência de estoque.");
                }
            }

            // Baixa de Estoque (Mesmo ficando negativo)
            produto.setQuantidadeEmEstoque(estoqueFuturo);
            produtoRepository.save(produto);

            // Monta Item
            ItemVenda item = new ItemVenda();
            item.setVenda(venda);
            item.setProduto(produto);
            item.setQuantidade(quantidadeVendida);
            item.setPrecoUnitario(itemDTO.getPrecoUnitario());
            item.setDescontoItem(itemDTO.getDescontoItem() != null ? itemDTO.getDescontoItem() : BigDecimal.ZERO);
            item.setCustoUnitario(produto.getPrecoMedioPonderado());
            item.setCustoTotal(produto.getPrecoMedioPonderado().multiply(quantidadeVendida));

            BigDecimal totalItem = item.getPrecoUnitario().multiply(item.getQuantidade()).subtract(item.getDescontoItem());
            item.setValorTotalItem(totalItem);

            itensParaSalvar.add(item);
            somaTotal = somaTotal.add(totalItem);

            // Kardex
            MovimentoEstoque mov = new MovimentoEstoque();
            mov.setProduto(produto);
            mov.setTipoMovimento("SAIDA_VENDA");
            mov.setQuantidadeMovimentada(quantidadeVendida);
            mov.setDataMovimento(LocalDateTime.now());
            mov.setCustoMovimentado(produto.getPrecoMedioPonderado());
            movimentoEstoqueRepository.save(mov);
        }

        venda.setItens(itensParaSalvar);
        venda.setValorTotal(somaTotal);
        venda.setValorLiquido(somaTotal.subtract(venda.getDesconto()));

        // Precisamos adicionar o campo statusFiscal na Entidade Venda.java se ainda não tiver!
        // venda.setStatusFiscal(statusFiscalInicial);

        vendaRepository.save(venda);

        return VendaResponseDTO.builder()
                .idVenda(venda.getId())
                .dataVenda(venda.getDataVenda())
                .valorTotal(venda.getValorLiquido())
                .operador(venda.getOperador().getMatricula())
                .totalItens(venda.getItens().size())
                .alertas(alertasParaCaixa) // Envia os alertas para o caixa
                .statusFiscal(statusFiscalInicial)
                .build();
    }
}