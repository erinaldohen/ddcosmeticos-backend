package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EntradaNFRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemEntradaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import corrigido para Spring Transaction

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

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

    @Autowired
    private FornecedorRepository fornecedorRepository;

    /**
     * Processa a entrada de uma Nota Fiscal, atualiza estoque e recalcula o PMP para cada item.
     * @param requestDTO Dados da NF de entrada.
     */
    @Transactional
    public void registrarEntradaNF(EntradaNFRequestDTO requestDTO) {

        // 1. Auditoria e Validação de Fornecedor
        Usuario operador = usuarioRepository.findByMatricula(requestDTO.getMatriculaOperador())
                .orElseThrow(() -> new ResourceNotFoundException("Operador de auditoria não encontrado: " + requestDTO.getMatriculaOperador()));

        Fornecedor fornecedor = fornecedorRepository.findByCpfOuCnpj(requestDTO.getCnpjCpfFornecedor())
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado: " + requestDTO.getCnpjCpfFornecedor()));

        // 2. Processamento dos Itens da NF
        for (ItemEntradaDTO itemDTO : requestDTO.getItens()) {

            // CORREÇÃO AQUI: Usar .orElseThrow() para tratar o Optional retornado
            Produto produto = produtoRepository.findByCodigoBarras(itemDTO.getCodigoBarras())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado para o código de barras: " + itemDTO.getCodigoBarras()));

            if (itemDTO.getCustoUnitario().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Custo unitário deve ser maior que zero para o produto: " + produto.getDescricao());
            }

            // Valores da Entrada
            BigDecimal qtdeEntrada = itemDTO.getQuantidade();
            BigDecimal custoEntradaUnitario = itemDTO.getCustoUnitario().setScale(PMP_SCALE, PMP_ROUNDING_MODE);

            // PMP é recálculado APENAS se houver quantidade ou custo válido na entrada.
            if (qtdeEntrada.compareTo(BigDecimal.ZERO) <= 0 || custoEntradaUnitario.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 3. RECÁLCULO DO PMP (LÓGICA CRÍTICA)
            // Trata nulos caso seja a primeira entrada
            BigDecimal qtdeAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
            BigDecimal pmpAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

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
                novoPMP = novoValorTotal.divide(novaQtdeTotal, PMP_SCALE, PMP_ROUNDING_MODE);
            } else {
                novoPMP = BigDecimal.ZERO;
            }

            // 4. ATUALIZAÇÃO E PERSISTÊNCIA DO PRODUTO
            produto.setPrecoMedioPonderado(novoPMP);
            produto.setQuantidadeEmEstoque(novaQtdeTotal);
            produtoRepository.save(produto);

            // 5. REGISTRO DE MOVIMENTO DE ESTOQUE (AUDITORIA)
            MovimentoEstoque movimento = new MovimentoEstoque();
            movimento.setProduto(produto);
            movimento.setDataMovimento(LocalDateTime.now()); // Corrigido para setDataMovimento
            movimento.setQuantidadeMovimentada(qtdeEntrada); // Positivo para entrada
            movimento.setCustoMovimentado(valorTotalEntrada.setScale(PMP_SCALE, PMP_ROUNDING_MODE));
            movimento.setTipoMovimento("ENTRADA_NF");

            // Ajuste para ID Referencia (Long) - Usando hash simples ou 0 temporariamente pois não temos entidade NF
            // O ideal seria criar uma entidade NotaFiscalEntrada no futuro
            movimento.setIdReferencia(0L);

            movimentoEstoqueRepository.save(movimento);
        }
    }
}