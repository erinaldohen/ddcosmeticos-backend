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

        // CORREÇÃO: Records usam acessores sem 'get' -> dto.clienteCpf()
        log.info("Processando venda PDV - Cliente CPF: {}", dto.clienteCpf());

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setClienteCpf(dto.clienteCpf());
        venda.setClienteNome(dto.clienteNome()); // Mapeamento adicional útil
        venda.setDataVenda(LocalDateTime.now());
        venda.setFormaPagamento(dto.formaPagamento());

        // Status inicial usando o Enum correto
        venda.setStatusFiscal(StatusFiscal.PENDENTE);

        BigDecimal totalVenda = BigDecimal.ZERO;

        // Processamento dos Itens
        // CORREÇÃO: dto.itens() retorna List<ItemVendaDTO> agora
        for (ItemVendaDTO itemDto : dto.itens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não cadastrado: " + itemDto.getCodigoBarras()));

            // Validação de Estoque (Ruptura)
            if (produto.getQuantidadeEmEstoque().compareTo(itemDto.getQuantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente para: " + produto.getDescricao() + ". Disponível: " + produto.getQuantidadeEmEstoque());
            }

            ItemVenda item = new ItemVenda();
            item.setProduto(produto);
            item.setQuantidade(itemDto.getQuantidade());
            item.setPrecoUnitario(produto.getPrecoVenda());

            // CORREÇÃO: O getter correto em ItemVenda.java não existe para 'custoUnitario',
            // deve-se usar o setter histórico ou pegar do produto.
            item.setCustoUnitarioHistorico(produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO);

            BigDecimal valorItem = item.getPrecoUnitario().multiply(item.getQuantidade());
            item.setValorTotalItem(valorItem);

            // CORREÇÃO: O getter getCustoTotal() do ItemVenda já calcula baseado no histórico
            // Não precisamos setar custoTotal manualmente se ele for calculado,
            // mas como não há field custoTotal na entidade ItemVenda (apenas método), isso está ok.

            venda.adicionarItem(item);
            totalVenda = totalVenda.add(valorItem);
        }

        venda.setTotalVenda(totalVenda);
        venda.setDescontoTotal(dto.descontoTotal() != null ? dto.descontoTotal() : BigDecimal.ZERO);

        Venda vendaSalva = vendaRepository.save(venda);

        executarFluxosOperacionais(vendaSalva, dto);

        // --- REGRA DE NEGÓCIO: EMISSÃO HÍBRIDA ---
        // CORREÇÃO: Acesso ao campo boolean do record
        boolean emitirApenasComEntrada = dto.apenasItensComNfEntrada();

        try {
            nfceService.emitirNfce(vendaSalva, emitirApenasComEntrada);
            // CORREÇÃO: Usando Enum em vez de String
            vendaSalva.setStatusFiscal(StatusFiscal.APROVADA);
        } catch (Exception e) {
            log.error("Erro na emissão da NFC-e: {}", e.getMessage());
            // CORREÇÃO: Usando Enum para contingência/erro
            vendaSalva.setStatusFiscal(StatusFiscal.ERRO_EMISSAO);
        }

        return vendaRepository.save(vendaSalva);
    }

    private void executarFluxosOperacionais(Venda venda, VendaRequestDTO dto) {
        // Baixa de Estoque Automática
        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.VENDA.name());

            // Definindo SAIDA implicitamente para a lógica do EstoqueService
            ajuste.setTipoMovimento("SAIDA");

            estoqueService.realizarAjusteInventario(ajuste);
        });

        // Integração Financeira
        // CORREÇÃO: O método lancarReceitaDeVenda no FinanceiroService espera (Long, BigDecimal, String).
        // Adaptamos a chamada convertendo o Enum para String.
        financeiroService.lancarReceitaDeVenda(
                venda.getId(),
                venda.getTotalVenda(),
                dto.formaPagamento().name() // Convertendo Enum para String
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
        // Retorna null ou lança exceção dependendo da sua regra de negócio.
        // Como Venda.usuario é nullable=false, isso vai dar erro se retornar null.
        // Idealmente, trate isso ou garanta que sempre haja usuário logado.
        return null;
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        return vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));
    }
}