package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Transactional
    public Venda realizarVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();

        log.info("Processando venda PDV - Cliente CPF: {}", dto.clienteCpf());

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteCpf(dto.clienteCpf());
        venda.setClienteNome(dto.clienteNome());
        venda.setDataVenda(LocalDateTime.now());
        venda.setFormaPagamento(dto.formaPagamento());
        venda.setStatusFiscal(StatusFiscal.PENDENTE);

        BigDecimal totalVenda = BigDecimal.ZERO;

        for (ItemVendaDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não cadastrado: " + itemDto.getCodigoBarras()));

            // 1. Validação de Estoque (Converte Integer do Produto para BigDecimal)
            BigDecimal estoqueAtual = produto.getQuantidadeEmEstoque() != null
                    ? new BigDecimal(produto.getQuantidadeEmEstoque())
                    : BigDecimal.ZERO;

            if (estoqueAtual.compareTo(itemDto.getQuantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente para: " + produto.getDescricao() + ". Disponível: " + estoqueAtual);
            }

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);

            // --- CORREÇÃO LINHA 70 ---
            // Removemos .intValue() pois a entidade ItemVenda agora usa BigDecimal
            item.setQuantidade(itemDto.getQuantidade());

            item.setPrecoUnitario(produto.getPrecoVenda());

            // 2. Custo Histórico (Usa o nome que você solicitou: getPrecoMedioPonderado)
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null
                    ? produto.getPrecoMedioPonderado()
                    : BigDecimal.ZERO);

            // Cálculo do valor total do item (Tudo em BigDecimal agora)
            BigDecimal valorItem = item.getPrecoUnitario().multiply(item.getQuantidade());

            venda.adicionarItem(item);
            totalVenda = totalVenda.add(valorItem);
        }

        venda.setTotalVenda(totalVenda);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        Venda vendaSalva = vendaRepository.save(venda);

        executarFluxosOperacionais(vendaSalva, dto);

        // Emissão Fiscal
        boolean emitirApenasComEntrada = dto.apenasItensComNfEntrada();
        try {
            nfceService.emitirNfce(vendaSalva, emitirApenasComEntrada);
            vendaSalva.setStatusFiscal(StatusFiscal.APROVADA);
        } catch (Exception e) {
            log.error("Erro na emissão da NFC-e: {}", e.getMessage());
            vendaSalva.setStatusFiscal(StatusFiscal.ERRO_EMISSAO);
        }

        return vendaRepository.save(vendaSalva);
    }

    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        // Baixa de Estoque
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());

            // Passa BigDecimal direto (AjusteEstoqueDTO espera BigDecimal)
            ajuste.setQuantidade(item.getQuantidade());

            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA.name());
            ajuste.setTipoMovimento("SAIDA");

            estoqueService.realizarAjusteInventario(ajuste);
        });

        // Financeiro
        financeiroService.lancarReceitaDeVenda(
                venda.getId(),
                venda.getTotalVenda(),
                dto.formaPagamento().name()
        );
    }

    private Usuario capturarUsuarioLogado() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Usuario) {
                return (Usuario) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.warn("Venda sem usuário identificado.");
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }
}