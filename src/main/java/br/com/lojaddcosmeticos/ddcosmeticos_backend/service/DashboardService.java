package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private PrecificacaoService precificacaoService;
    @Autowired private AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoGeral() {
        // Correção de Performance:
        // Removemos o produtoRepository.findAll() que trazia a lista inteira para a memória.
        // Agora usamos queries de agregação (COUNT e SUM) diretas no banco.

        return DashboardResumoDTO.builder()
                .produtosAbaixoMinimo(produtoRepository.contarProdutosAbaixoDoMinimo())
                .produtosEsgotados(produtoRepository.countByQuantidadeEmEstoqueLessThanEqualAndAtivoTrue(0))
                .valorTotalEstoqueCusto(produtoRepository.calcularValorTotalEstoque())
                .produtosMargemCritica((long) precificacaoService.buscarProdutosComMargemCritica().size())
                .produtosSemNcmOuCest(produtoRepository.contarProdutosSemFiscal())
                .build();
    }
}