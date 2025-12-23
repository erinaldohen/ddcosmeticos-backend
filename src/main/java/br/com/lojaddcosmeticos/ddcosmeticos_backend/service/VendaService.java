package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque; // <--- Importante
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
        // Captura usuário para auditoria da venda
        Usuario usuarioLogado = null;
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Usuario) {
                usuarioLogado = (Usuario) auth.getPrincipal();
            }
        } catch (Exception e) {
            log.warn("Venda sem usuário identificado no contexto de segurança.");
        }

        log.info("Processando venda PDV - Cliente CPF: {}", dto.getCpfCliente());

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado); // <--- VINCULA O VENDEDOR
        venda.setClienteCpf(dto.getCpfCliente());
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusFiscal("PENDENTE");

        BigDecimal totalVenda = BigDecimal.ZERO;

        for (ItemVendaDTO itemDto : dto.getItens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não cadastrado: " + itemDto.getCodigoBarras()));

            if (produto.getQuantidadeEmEstoque().compareTo(itemDto.getQuantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente para: " + produto.getDescricao());
            }

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());
            item.setCustoUnitario(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);

            BigDecimal valorItem = item.getPrecoUnitario().multiply(item.getQuantidade());
            item.setValorTotalItem(valorItem);
            item.setCustoTotal(item.getCustoUnitario().multiply(item.getQuantidade()));

            venda.adicionarItem(item);
            totalVenda = totalVenda.add(valorItem);
        }

        venda.setTotalVenda(totalVenda);
        venda.setDescontoTotal(BigDecimal.ZERO);

        Venda vendaSalva = vendaRepository.save(venda);

        executarFluxosOperacionais(vendaSalva, dto);

        try {
            nfceService.emitirNfce(vendaSalva);
        } catch (Exception e) {
            log.warn("SEFAZ Indisponível - Contingência. Erro: {}", e.getMessage());
            vendaSalva.setStatusFiscal("CONTINGENCIA");
            vendaRepository.save(vendaSalva);
        }

        return vendaSalva;
    }

    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        // Baixa de Estoque Automática
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());

            // --- CORREÇÃO AQUI ---
            // Antes era: ajuste.setTipoMovimento("SAIDA_VENDA"); (Errado)
            // Agora usamos o campo motivo com o valor do Enum correto:
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA.name());

            estoqueService.realizarAjusteInventario(ajuste);
        });

        financeiroService.lancarReceitaDeVenda(
                venda.getId(),
                venda.getTotalVenda(),
                dto.getFormaPagamento(),
                dto.getQuantidadeParcelas()
        );
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }
}