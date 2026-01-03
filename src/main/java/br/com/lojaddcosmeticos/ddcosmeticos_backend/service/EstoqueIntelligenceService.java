package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class EstoqueIntelligenceService {

    @Autowired
    private ProdutoRepository produtoRepository;

    // Configuração da IA: Quantos dias de margem de segurança queremos?
    // Ex: Se o fornecedor demora 7 dias, queremos ter estoque para 7 + 5 dias de segurança.
    private static final int DIAS_MARGEM_SEGURANCA = 5;

    public List<SugestaoCompraDTO> gerarRelatorioCompras() {
        // Busca apenas produtos ativos para não sugerir compra de produto descontinuado
        // Obs: Idealmente criar um método 'findByAtivoTrue' no repository se não houver
        List<Produto> produtos = produtoRepository.findAll();
        List<SugestaoCompraDTO> sugestoes = new ArrayList<>();

        for (Produto p : produtos) {
            if (!p.isAtivo()) continue;

            // Garante que os totais estão atualizados
            p.atualizarSaldoTotal();
            p.recalcularEstoqueMinimoSugerido(); // A IA recalcula o mínimo baseada na média diária atual

            // Se o produto não tem saída (venda média 0), ignora para não empilhar estoque
            if (p.getVendaMediaDiaria() == null || p.getVendaMediaDiaria().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Lógica de Ruptura
            if (p.getQuantidadeEmEstoque() <= p.getEstoqueMinimo()) {
                sugestoes.add(calcularSugestao(p));
            }
        }

        // Ordena por urgência: Quem tem menos estoque em relação à venda aparece primeiro
        sugestoes.sort((a, b) -> b.getNivelUrgencia().compareTo(a.getNivelUrgencia()));

        return sugestoes;
    }

    private SugestaoCompraDTO calcularSugestao(Produto p) {
        // Cálculo da IA:
        // Objetivo: Cobrir o tempo de reposição + margem de segurança
        int diasCoberturaAlvo = p.getDiasParaReposicao() + DIAS_MARGEM_SEGURANCA;

        BigDecimal vendaDiaria = p.getVendaMediaDiaria();

        // Quanto preciso ter no total para ficar tranquilo?
        int estoqueAlvo = vendaDiaria.multiply(new BigDecimal(diasCoberturaAlvo)).intValue();

        // Quanto falta comprar?
        int quantidadeComprar = estoqueAlvo - p.getQuantidadeEmEstoque();

        if (quantidadeComprar <= 0) quantidadeComprar = 10; // Compra mínima se deu erro de cálculo

        // Definir Urgência
        String urgencia = "NORMAL";
        int diasRestantesEstoque = 0;
        if (vendaDiaria.compareTo(BigDecimal.ZERO) > 0) {
            diasRestantesEstoque = new BigDecimal(p.getQuantidadeEmEstoque()).divide(vendaDiaria, 0, BigDecimal.ROUND_DOWN).intValue();
        }

        if (p.getQuantidadeEmEstoque() == 0) {
            urgencia = "CRÍTICO (RUPTURA)";
        } else if (diasRestantesEstoque < p.getDiasParaReposicao()) {
            urgencia = "ALERTA (VAI FALTAR ANTES DE CHEGAR)";
        }

        // Custo estimado
        BigDecimal custoTotal = p.getPrecoCusto() != null
                ? p.getPrecoCusto().multiply(new BigDecimal(quantidadeComprar))
                : BigDecimal.ZERO;

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