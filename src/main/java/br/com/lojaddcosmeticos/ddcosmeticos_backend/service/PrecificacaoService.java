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

    private static final BigDecimal MARKUP_PADRAO = new BigDecimal("1.50");
    private static final BigDecimal MARGEM_MINIMA_ALERTA = new BigDecimal("0.20");

    public List<AnalisePrecificacaoDTO> buscarProdutosComMargemCritica() {
        List<Produto> produtos = produtoRepository.findAll();
        List<AnalisePrecificacaoDTO> alertas = new ArrayList<>();

        for (Produto p : produtos) {
            if (!p.isAtivo()) continue;

            if (p.getPrecoCusto() == null || p.getPrecoCusto().compareTo(BigDecimal.ZERO) == 0) continue;

            AnalisePrecificacaoDTO analise = analisarProduto(p);

            if (!analise.getStatusMargem().equals("SAUDÁVEL") && !analise.getStatusMargem().equals("EXCELENTE")) {
                alertas.add(analise);
            }
        }

        return alertas;
    }

    public AnalisePrecificacaoDTO analisarProduto(Produto p) {
        BigDecimal custo = p.getPrecoCusto();
        BigDecimal venda = p.getPrecoVenda();

        BigDecimal lucroDinheiro = venda.subtract(custo);

        BigDecimal margemPercentual = BigDecimal.ZERO;
        if (venda.compareTo(BigDecimal.ZERO) > 0) {
            margemPercentual = lucroDinheiro.divide(venda, 4, RoundingMode.HALF_EVEN);
        }

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

        BigDecimal precoSugerido = custo.multiply(MARKUP_PADRAO).setScale(2, RoundingMode.HALF_UP);

        return new AnalisePrecificacaoDTO(
                p.getCodigoBarras(),
                p.getDescricao(),
                custo,
                venda,
                margemPercentual.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_EVEN),
                lucroDinheiro,
                status,
                precoSugerido
        );
    }

    public AnalisePrecificacaoDTO calcularSugestao(String codigoBarras) {
        Produto produto = produtoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado: " + codigoBarras));

        return analisarProduto(produto);
    }

}