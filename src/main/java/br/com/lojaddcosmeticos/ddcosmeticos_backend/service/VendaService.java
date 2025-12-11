package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemVendaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.VendaResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class VendaService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    // Injeção opcional caso o cascade da Venda não seja suficiente,
    // mas mantemos aqui para garantir o contexto.
    @Autowired
    private ItemVendaRepository itemVendaRepository;

    /**
     * Registra uma nova venda no sistema.
     * Realiza a baixa de estoque, cálculo de valores e auditoria.
     * * @param requestDTO Dados da venda recebidos da API.
     * @return DTO com os dados da venda registrada.
     */
    @Transactional
    public VendaResponseDTO registrarVenda(VendaRequestDTO requestDTO) {

        // 1. Identificar o Operador (Auditoria)
        // Recupera o usuário logado através do Token JWT validado pelo SecurityFilter
        String matriculaOperador = SecurityContextHolder.getContext().getAuthentication().getName();

        Usuario operador = usuarioRepository.findByMatricula(matriculaOperador)
                .orElseThrow(() -> new ResourceNotFoundException("Operador não encontrado com a matrícula: " + matriculaOperador));

        // 2. Inicializar a Entidade Venda
        Venda venda = new Venda();
        venda.setOperador(operador);
        venda.setDataVenda(LocalDateTime.now()); // Corrigido para corresponder a Venda.java

        // Inicializa a lista de itens e variáveis de cálculo
        List<ItemVenda> itensVenda = new ArrayList<>();
        BigDecimal valorTotalBruto = BigDecimal.ZERO;

        // 3. Processamento dos Itens da Venda
        for (ItemVendaDTO itemDTO : requestDTO.getItens()) {

            // Busca o Produto (Lança erro 404 se não existir)
            Produto produto = produtoRepository.findByCodigoBarras(itemDTO.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado: " + itemDTO.getCodigoBarras()));

            // Validação de Estoque (Erro 400 se insuficiente)
            // Nota: Removemos a checagem de 'movimentaEstoque' pois o campo não existe em Produto.java
            if (produto.getQuantidadeEmEstoque() == null ||
                    produto.getQuantidadeEmEstoque().compareTo(itemDTO.getQuantidade()) < 0) {
                throw new ValidationException("Estoque insuficiente para o produto: " + produto.getDescricao());
            }

            // Cálculos Financeiros do Item
            BigDecimal valorBrutoItem = itemDTO.getPrecoUnitario().multiply(itemDTO.getQuantidade());
            BigDecimal valorLiquidoItem = valorBrutoItem.subtract(itemDTO.getDescontoItem());

            // Custo da Mercadoria Vendida (CMV/PMP)
            BigDecimal custoUnitario = produto.getPrecoMedioPonderado();
            if (custoUnitario == null) custoUnitario = BigDecimal.ZERO;
            BigDecimal custoTotalItem = custoUnitario.multiply(itemDTO.getQuantidade());

            // Criação da Entidade ItemVenda
            ItemVenda itemVenda = new ItemVenda();
            itemVenda.setVenda(venda); // Vínculo bidirecional
            itemVenda.setProduto(produto);
            itemVenda.setQuantidade(itemDTO.getQuantidade());
            itemVenda.setPrecoUnitario(itemDTO.getPrecoUnitario());
            itemVenda.setDescontoItem(itemDTO.getDescontoItem());
            itemVenda.setValorTotalItem(valorLiquidoItem);
            // Auditoria de Custo (Snapshot do PMP no momento da venda)
            itemVenda.setCustoUnitario(custoUnitario);
            itemVenda.setCustoTotal(custoTotalItem);

            itensVenda.add(itemVenda);

            // Atualiza acumuladores
            valorTotalBruto = valorTotalBruto.add(valorBrutoItem);

            // 4. Baixa de Estoque do Produto
            produto.setQuantidadeEmEstoque(produto.getQuantidadeEmEstoque().subtract(itemDTO.getQuantidade()));
            produtoRepository.save(produto);
        }

        // 5. Fechamento da Venda
        BigDecimal descontoGlobal = requestDTO.getDesconto();
        if (descontoGlobal == null) descontoGlobal = BigDecimal.ZERO;

        BigDecimal valorLiquidoFinal = valorTotalBruto.subtract(descontoGlobal);

        // Validação final de consistência
        if (valorLiquidoFinal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("O valor total da venda não pode ser negativo.");
        }

        venda.setValorTotal(valorTotalBruto);
        venda.setDesconto(descontoGlobal); // Corrigido para corresponder a Venda.java
        venda.setValorLiquido(valorLiquidoFinal);
        venda.setItens(itensVenda);

        // 6. Persistência da Venda (Cascade salva os itens)
        Venda vendaSalva = vendaRepository.save(venda);

        // 7. Registro de Movimentação de Estoque (Pós-Venda)
        // Fazemos isso aqui para ter acesso ao ID da Venda (vendaSalva.getId())
        for (ItemVenda item : vendaSalva.getItens()) {
            MovimentoEstoque movimento = new MovimentoEstoque();
            movimento.setProduto(item.getProduto());
            movimento.setDataMovimento(LocalDateTime.now()); // Corrigido para corresponder a MovimentoEstoque.java
            movimento.setTipoMovimento("VENDA_PDV");
            movimento.setQuantidadeMovimentada(item.getQuantidade().negate()); // Saída é negativa
            movimento.setCustoMovimentado(item.getCustoTotal().negate()); // Custo de saída

            // Vincula o movimento ao ID da venda realizada para rastreabilidade
            movimento.setIdReferencia(vendaSalva.getId()); // Corrigido para usar setIdReferencia (Long)

            movimentoEstoqueRepository.save(movimento);
        }

        // 8. Retorno do DTO mapeado
        return new VendaResponseDTO(vendaSalva);
    }
}