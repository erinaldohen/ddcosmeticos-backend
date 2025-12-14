package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO; // Importe o DTO de Resposta
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
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
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private NfceService nfceService;

    @Transactional
    // MUDANÇA 1: Nome alterado para 'registrarVenda' (igual ao Controller)
    // MUDANÇA 2: Retorno alterado para 'VendaResponseDTO'
    public VendaResponseDTO registrarVenda(VendaRequestDTO dadosVenda) {

        // 1. Identificar o Vendedor/Caixa
        String matriculaOperador = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario operador = usuarioRepository.findByMatricula(matriculaOperador)
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado"));

        Venda venda = new Venda();
        venda.setOperador(operador);
        venda.setDataVenda(LocalDateTime.now());
        venda.setFormaPagamento(dadosVenda.getFormaPagamento());

        BigDecimal desconto = dadosVenda.getDesconto() != null ? dadosVenda.getDesconto() : BigDecimal.ZERO;
        venda.setDescontoTotal(desconto);

        venda.setStatusFiscal("PENDENTE");

        BigDecimal totalItens = BigDecimal.ZERO;
        List<String> alertas = new ArrayList<>();

        // 2. Processar Itens
        if (dadosVenda.getItens() != null) {
            for (ItemVendaDTO itemDTO : dadosVenda.getItens()) {
                Produto produto = produtoRepository.findByCodigoBarras(itemDTO.getCodigoBarras())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDTO.getCodigoBarras()));

                // Baixa de Estoque
                if (produto.getQuantidadeEmEstoque().compareTo(itemDTO.getQuantidade()) < 0) {
                    throw new IllegalStateException("Estoque insuficiente para: " + produto.getDescricao());
                }
                produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(itemDTO.getQuantidade()));
                produtoRepository.save(produto);

                // Criar ItemVenda
                ItemVenda item = new ItemVenda();
                item.setProduto(produto);
                item.setQuantidade(itemDTO.getQuantidade());
                item.setPrecoUnitario(produto.getPrecoVenda());
                item.setDescontoItem(BigDecimal.ZERO);

                BigDecimal totalItem = item.getPrecoUnitario().multiply(item.getQuantidade());
                item.setValorTotalItem(totalItem);

                // Preencher Campos de Custo Obrigatórios
                BigDecimal custoUnit = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;
                item.setCustoUnitario(custoUnit);
                item.setCustoTotal(custoUnit.multiply(item.getQuantidade()));

                item.setVenda(venda);

                venda.getItens().add(item);
                totalItens = totalItens.add(totalItem);

                // Registrar Kardex (Saída)
                MovimentoEstoque mov = new MovimentoEstoque();
                mov.setProduto(produto);
                mov.setTipoMovimento("SAIDA_VENDA");
                mov.setQuantidadeMovimentada(itemDTO.getQuantidade());
                mov.setDataMovimento(LocalDateTime.now());
                mov.setCustoMovimentado(custoUnit);
                movimentoEstoqueRepository.save(mov);
            }
        }

        // 3. Fechar Valores e Salvar
        venda.setTotalVenda(totalItens.subtract(venda.getDescontoTotal()));
        venda = vendaRepository.save(venda);

        // 4. Emissão Fiscal (NFC-e)
        boolean temProdutoFiscal = venda.getItens().stream()
                .anyMatch(i -> i.getProduto().isPossuiNfEntrada());

        if (temProdutoFiscal) {
            try {
                NfceResponseDTO respostaFiscal = nfceService.emitirNfce(venda);
                // Atualiza status se necessário com base na resposta
            } catch (Exception e) {
                alertas.add("Erro na emissão fiscal: " + e.getMessage());
            }
        } else {
            venda.setStatusFiscal("NAO_FISCAL_GERENCIAL");
            alertas.add("Venda processada sem emissão de nota (Produtos sem lastro).");
        }

        Venda vendaSalva = vendaRepository.save(venda);

        // MUDANÇA 3: Converter a Entidade para DTO antes de retornar
        return VendaResponseDTO.builder()
                .idVenda(vendaSalva.getId())
                .dataVenda(vendaSalva.getDataVenda())
                .valorTotal(vendaSalva.getTotalVenda())
                .desconto(vendaSalva.getDescontoTotal())
                .operador(vendaSalva.getOperador().getNome()) // Supõe que Usuario tem getNome()
                .totalItens(vendaSalva.getItens().size())
                .statusFiscal(vendaSalva.getStatusFiscal())
                .alertas(alertas)
                .build();
    }
}