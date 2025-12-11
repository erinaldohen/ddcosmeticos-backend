package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class EstoqueService {

    @Autowired
    private ProdutoRepository produtoRepository;

    /**
     * Registra a entrada de estoque e recalcula o PMP (se necessário).
     */
    @Transactional
    public Produto registrarEntrada(EstoqueRequestDTO requestDTO) {

        // CORREÇÃO AQUI: Usar .orElseThrow() para desembrulhar o Optional<Produto>
        Produto produto = produtoRepository.findByCodigoBarras(requestDTO.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com código de barras: " + requestDTO.getCodigoBarras()));

        // Lógica de Atualização de Estoque
        BigDecimal qtdAtual = produto.getQuantidadeEmEstoque() != null ? produto.getQuantidadeEmEstoque() : BigDecimal.ZERO;
        BigDecimal qtdEntrada = requestDTO.getQuantidade();

        // Atualiza quantidade
        produto.setQuantidadeEmEstoque(qtdAtual.add(qtdEntrada));

        // Lógica simplificada de PMP (Preço Médio Ponderado)
        // Se houver custo na entrada, recalcula o médio
        if (requestDTO.getCustoUnitario() != null && requestDTO.getCustoUnitario().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal custoAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;

            BigDecimal valorTotalEstoque = custoAtual.multiply(qtdAtual);
            BigDecimal valorTotalEntrada = requestDTO.getCustoUnitario().multiply(qtdEntrada);

            BigDecimal novoTotal = valorTotalEstoque.add(valorTotalEntrada);
            BigDecimal novaQtd = qtdAtual.add(qtdEntrada);

            // Evita divisão por zero
            if (novaQtd.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal novoPMP = novoTotal.divide(novaQtd, 4, RoundingMode.HALF_UP);
                produto.setPrecoMedioPonderado(novoPMP);
            }
        }

        return produtoRepository.save(produto);
    }
}