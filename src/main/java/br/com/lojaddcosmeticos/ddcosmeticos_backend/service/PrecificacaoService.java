package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AnalisePrecificacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PrecificacaoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    // Configurações da Loja (Idealmente viriam de uma tabela de config)
    private static final BigDecimal MARKUP_PADRAO = new BigDecimal("1.50"); // Quero vender por 50% acima do custo
    private static final BigDecimal MARGEM_MINIMA_ALERTA = new BigDecimal("0.20"); // 20%

    public List<AnalisePrecificacaoDTO> buscarProdutosComMargemCritica() {
        List<Produto> produtos = produtoRepository.findAll();
        List<AnalisePrecificacaoDTO> alertas = new ArrayList<>();

        for (Produto p : produtos) {
            if (!p.isAtivo()) continue;

            // Ignora produtos com custo zero (erro de cadastro ou bonificação)
            if (p.getPrecoCusto() == null || p.getPrecoCusto().compareTo(BigDecimal.ZERO) == 0) continue;

            AnalisePrecificacaoDTO analise = analisarProduto(p);

            // Só adiciona na lista se não for saudável (Crítico ou Baixo)
            if (!analise.getStatusMargem().equals("SAUDÁVEL") && !analise.getStatusMargem().equals("EXCELENTE")) {
                alertas.add(analise);
            }
        }

        return alertas;
    }

    public AnalisePrecificacaoDTO analisarProduto(Produto p) {
        BigDecimal custo = p.getPrecoCusto();
        BigDecimal venda = p.getPrecoVenda();

        // 1. Calcular Lucro Bruto (Venda - Custo)
        BigDecimal lucroDinheiro = venda.subtract(custo);

        // 2. Calcular Margem % (Lucro / Venda)
        BigDecimal margemPercentual = BigDecimal.ZERO;
        if (venda.compareTo(BigDecimal.ZERO) > 0) {
            margemPercentual = lucroDinheiro.divide(venda, 4, RoundingMode.HALF_EVEN);
        }

        // 3. Definir Status
        String status;
        if (lucroDinheiro.compareTo(BigDecimal.ZERO) <= 0) {
            status = "CRÍTICO (PREJUÍZO)";
        } else if (margemPercentual.compareTo(MARGEM_MINIMA_ALERTA) < 0) {
            status = "ALERTA (MARGEM BAIXA)";
        } else if (margemPercentual.compareTo(new BigDecimal("0.40")) > 0) {
            status = "EXCELENTE";
        } else {
            status = "SAUDÁVEL";
        }

        // 4. Calcular Sugestão de Preço (Custo * Markup)
        BigDecimal precoSugerido = custo.multiply(MARKUP_PADRAO).setScale(2, RoundingMode.HALF_UP);

        return new AnalisePrecificacaoDTO(
                p.getCodigoBarras(),
                p.getDescricao(),
                custo,
                venda,
                margemPercentual.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_EVEN), // Converte 0.20 para 20.00
                lucroDinheiro,
                status,
                precoSugerido
        );
    }
    public AnalisePrecificacaoDTO calcularSugestao(String codigoBarras) {
        Produto produto = produtoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado: " + codigoBarras));

        // Reutiliza a lógica de cálculo que já existe
        return analisarProduto(produto);
    }

}