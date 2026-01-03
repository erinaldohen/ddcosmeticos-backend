package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DashboardService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private AuditoriaService auditoriaService;

    public DashboardResumoDTO obterResumoGeral() {
        List<Produto> todos = produtoRepository.findAll();

        // Cálculo de Valor Total em Estoque
        BigDecimal valorEstoque = todos.stream()
                .filter(p -> p.getPrecoCusto() != null)
                .map(p -> p.getPrecoCusto().multiply(BigDecimal.valueOf(p.getQuantidadeEmEstoque())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardResumoDTO.builder()
                .produtosAbaixoMinimo(produtoRepository.contarProdutosAbaixoDoMinimo())
                .produtosEsgotados(todos.stream().filter(p -> p.getQuantidadeEmEstoque() <= 0).count())
                .valorTotalEstoqueCusto(valorEstoque)
                .produtosMargemCritica((long) precificacaoService.buscarProdutosComMargemCritica().size())
                .produtosSemNcmOuCest(todos.stream().filter(p -> p.getNcm() == null || p.getCest() == null).count())
                .build();
    }
}