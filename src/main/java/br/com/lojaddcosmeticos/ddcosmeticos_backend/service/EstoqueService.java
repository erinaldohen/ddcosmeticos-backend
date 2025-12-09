// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/service/EstoqueService.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serviço responsável pela lógica de entradas de estoque, saídas não-venda e
 * pelo cálculo do Preço Médio Ponderado (PMP).
 */
@Service
public class EstoqueService {

    private static final int SCALE = 4; // PMP deve ter alta precisão para evitar erro de centavos
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    /**
     * Registra uma entrada de estoque e recalcula o Preço Médio Ponderado (PMP).
     *
     * @param requestDTO O DTO contendo o produto, quantidade e custo de entrada.
     * @return O Produto atualizado com o novo PMP.
     * @throws RuntimeException Se o produto não for encontrado.
     */
    @Transactional
    public Produto registrarEntrada(EstoqueRequestDTO requestDTO) {

        // 1. Busca do Produto
        Produto produto = produtoRepository.findByCodigoBarras(requestDTO.getCodigoBarras());
        if (produto == null) {
            throw new RuntimeException("Produto não encontrado com EAN: " + requestDTO.getCodigoBarras());
        }

        // 2. Cálculo do Novo PMP

        BigDecimal qtdEntrada = requestDTO.getQuantidade();
        BigDecimal custoEntrada = requestDTO.getCustoUnitario().setScale(SCALE, ROUNDING_MODE);

        BigDecimal qtdAtual = produto.getQuantidadeEmEstoque();
        BigDecimal pmpAtual = produto.getPrecoMedioPonderado().setScale(SCALE, ROUNDING_MODE);

        // CUSTO TOTAL ATUAL: Custo do estoque que já existe
        BigDecimal custoTotalAtual = qtdAtual.multiply(pmpAtual);

        // CUSTO TOTAL DA ENTRADA: Custo da nova mercadoria
        BigDecimal custoTotalEntrada = qtdEntrada.multiply(custoEntrada);

        // NOVO ESTOQUE TOTAL
        BigDecimal novoEstoqueTotal = qtdAtual.add(qtdEntrada);

        // CÁLCULO PMP: (Custo Total Atual + Custo Total da Entrada) / Novo Estoque Total
        BigDecimal novoPMP;
        if (novoEstoqueTotal.compareTo(BigDecimal.ZERO) > 0) {
            novoPMP = custoTotalAtual.add(custoTotalEntrada)
                    .divide(novoEstoqueTotal, SCALE, ROUNDING_MODE);
        } else {
            // Caso raro, onde o estoque inicial é zero e a entrada também é zero, ou se o novo estoque for zero.
            novoPMP = custoEntrada;
        }

        // 3. Atualização do Produto
        produto.setQuantidadeEmEstoque(novoEstoqueTotal); // Soma a quantidade
        produto.setPrecoMedioPonderado(novoPMP);         // Define o novo PMP

        Produto produtoAtualizado = produtoRepository.save(produto);

        // 4. Registro do Movimento de Estoque (Auditoria)
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produtoAtualizado);
        movimento.setQuantidadeMovimentada(qtdEntrada); // Entrada é um valor positivo
        movimento.setTipoMovimento("ENTRADA_NF");
        // Em um sistema real, aqui iria o ID da NF de Compra
        movimento.setIdReferencia(null);

        movimentoEstoqueRepository.save(movimento);

        return produtoAtualizado;
    }
}