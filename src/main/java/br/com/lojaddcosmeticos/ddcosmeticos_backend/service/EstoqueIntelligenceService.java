package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class EstoqueIntelligenceService {

    @Autowired
    private ProdutoRepository produtoRepository;

    private static final int DIAS_MARGEM_SEGURANCA = 5;

    @Transactional // Adicionado para garantir que atualizarSaldoTotal não falhe com Lazy Loading
    public List<SugestaoCompraDTO> gerarRelatorioCompras() {
        List<Produto> produtos = produtoRepository.findAll();
        List<SugestaoCompraDTO> sugestoes = new ArrayList<>();

        for (Produto p : produtos) {
            // Filtros de produto
            if (!p.isAtivo()) continue;

            // Atualiza os saldos em memória para garantir precisão
            p.atualizarSaldoTotal();

            // Recalcula o mínimo baseado na média atual (lógica trazida da entidade)
            p.recalcularEstoqueMinimoSugerido();

            // Ignora produtos sem média de venda definida ou zerada
            if (p.getVendaMediaDiaria() == null || p.getVendaMediaDiaria().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Lógica de Ruptura (Se estoque atual <= mínimo)
            int min = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;
            if (p.getQuantidadeEmEstoque() <= min) {
                sugestoes.add(calcularSugestao(p));
            }
        }

        // Ordenação segura (evitando NullPointerException na comparação)
        sugestoes.sort(Comparator.comparing(
                (SugestaoCompraDTO dto) -> dto.getNivelUrgencia() != null ? dto.getNivelUrgencia() : "",
                Comparator.reverseOrder() // Ordem alfabética reversa para Z-CRÍTICO vir antes
        ));

        return sugestoes;
    }

    private SugestaoCompraDTO calcularSugestao(Produto p) {
        int diasReposicao = p.getDiasParaReposicao() != null ? p.getDiasParaReposicao() : 7;
        int diasCoberturaAlvo = diasReposicao + DIAS_MARGEM_SEGURANCA;

        BigDecimal vendaDiaria = p.getVendaMediaDiaria() != null ? p.getVendaMediaDiaria() : BigDecimal.ZERO;

        // Cálculo da Sugestão
        int estoqueAlvo = vendaDiaria.multiply(new BigDecimal(diasCoberturaAlvo)).intValue();
        int quantidadeComprar = estoqueAlvo - p.getQuantidadeEmEstoque();

        if (quantidadeComprar <= 0) quantidadeComprar = 6; // Mínimo de compra (setup de fábrica)

        // Definir Urgência
        String urgencia = "NORMAL";
        int diasRestantesEstoque = 0;

        if (vendaDiaria.compareTo(BigDecimal.ZERO) > 0 && p.getQuantidadeEmEstoque() > 0) {
            diasRestantesEstoque = new BigDecimal(p.getQuantidadeEmEstoque())
                    .divide(vendaDiaria, 0, RoundingMode.DOWN).intValue();
        }

        if (p.getQuantidadeEmEstoque() <= 0) {
            urgencia = "Z-CRÍTICO (RUPTURA)"; // Estoque zerado ou negativo
        } else if (diasRestantesEstoque < diasReposicao) {
            urgencia = "Y-ALERTA (VAI FALTAR)"; // Vai acabar antes do produto chegar
        }

        // Custo estimado
        BigDecimal custoUnitario = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
        BigDecimal custoTotal = custoUnitario.multiply(new BigDecimal(quantidadeComprar));

        return new SugestaoCompraDTO(
                p.getCodigoBarras(),
                p.getDescricao(),
                p.getMarca(),
                p.getQuantidadeEmEstoque(),
                p.getEstoqueMinimo(),
                quantidadeComprar,
                urgencia,
                custoTotal
        );
    }
}