// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/service/CustoService.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EntradaNFRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemEntradaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Serviço responsável pela entrada de mercadorias e recálculo do Preço Médio Ponderado (PMP).
 */
@Service
public class CustoService {

    private static final int PMP_SCALE = 4;
    private static final RoundingMode PMP_ROUNDING_MODE = RoundingMode.HALF_UP;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired // NOVO: Injeção do Fornecedor
    private FornecedorRepository fornecedorRepository;

    /**
     * Processa a entrada de uma Nota Fiscal, atualiza estoque e recalcula o PMP para cada item.
     * @param requestDTO Dados da NF de entrada.
     */
    @Transactional
    public void registrarEntradaNF(EntradaNFRequestDTO requestDTO) {

        // 1. Auditoria e Validação de Fornecedor
        Usuario operador = usuarioRepository.findByMatricula(requestDTO.getMatriculaOperador())
                .orElseThrow(() -> new RuntimeException("Operador de auditoria não encontrado: " + requestDTO.getMatriculaOperador()));

        // NOVO: Validação do Fornecedor
        Fornecedor fornecedor = fornecedorRepository.findByCnpjCpf(requestDTO.getCnpjCpfFornecedor())
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado: " + requestDTO.getCnpjCpfFornecedor()));

        // 2. Processamento dos Itens da NF (Restante do código é o mesmo)
        for (ItemEntradaDTO itemDTO : requestDTO.getItens()) {
            Produto produto = produtoRepository.findByCodigoBarras(itemDTO.getCodigoBarras());
            if (produto == null) {
                throw new RuntimeException("Produto não encontrado com EAN: " + itemDTO.getCodigoBarras());
            }

            // Valores da Entrada
            BigDecimal qtdeEntrada = itemDTO.getQuantidade();
            BigDecimal custoEntradaUnitario = itemDTO.getCustoUnitario().setScale(PMP_SCALE, PMP_ROUNDING_MODE);

            // PMP é recálculado APENAS se houver quantidade ou custo válido na entrada.
            if (qtdeEntrada.compareTo(BigDecimal.ZERO) <= 0 || custoEntradaUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                // Produtos com qtde ou custo zero na NF não alteram o PMP, apenas atualizam o estoque.
                // Neste caso, se for só ajuste de estoque, o PMP não é afetado.
                continue;
            }

            // 3. RECÁLCULO DO PMP (LÓGICA CRÍTICA)

            BigDecimal qtdeAtual = produto.getQuantidadeEmEstoque();
            BigDecimal pmpAtual = produto.getPrecoMedioPonderado();

            // 3a. Cálculo do Valor Total do Estoque Antigo
            BigDecimal valorTotalAntigo = qtdeAtual.multiply(pmpAtual);

            // 3b. Cálculo do Valor Total da Nova Entrada
            BigDecimal valorTotalEntrada = qtdeEntrada.multiply(custoEntradaUnitario);

            // 3c. Novas Quantidades e Valores Totais
            BigDecimal novaQtdeTotal = qtdeAtual.add(qtdeEntrada);
            BigDecimal novoValorTotal = valorTotalAntigo.add(valorTotalEntrada);

            // 3d. Novo PMP (Novo Valor Total / Nova Quantidade Total)
            BigDecimal novoPMP;
            if (novaQtdeTotal.compareTo(BigDecimal.ZERO) > 0) {
                // Se a nova quantidade total for maior que zero, calcula o PMP
                novoPMP = novoValorTotal.divide(novaQtdeTotal, PMP_SCALE, PMP_ROUNDING_MODE);
            } else {
                // Caso a NF zere ou resulte em qtde zero (impossível em entrada, mas para segurança)
                novoPMP = BigDecimal.ZERO;
            }

            // 4. ATUALIZAÇÃO E PERSISTÊNCIA DO PRODUTO
            produto.setPrecoMedioPonderado(novoPMP); // Persiste o novo PMP
            produto.setQuantidadeEmEstoque(novaQtdeTotal); // Persiste a nova quantidade
            produtoRepository.save(produto);

            // 5. REGISTRO DE MOVIMENTO DE ESTOQUE (AUDITORIA)
            MovimentoEstoque movimento = new MovimentoEstoque();
            movimento.setProduto(produto);
            movimento.setQuantidadeMovimentada(qtdeEntrada); // Positivo para entrada

            // Custo Movimentado (Positivo para entrada)
            movimento.setCustoMovimentado(valorTotalEntrada.setScale(PMP_SCALE, PMP_ROUNDING_MODE));

            movimento.setTipoMovimento("ENTRADA_NF");
            // Usamos a Chave de Acesso como referência, mas como não temos entidade NF, usamos um ID fictício para auditoria
            movimento.setIdReferencia(produto.getId() * 1000 + qtdeEntrada.intValue());

            movimentoEstoqueRepository.save(movimento);
        }
    }
}