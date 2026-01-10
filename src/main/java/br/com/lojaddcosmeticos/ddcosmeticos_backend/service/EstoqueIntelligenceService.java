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

    private static final int DIAS_MARGEM_SEGURANCA = 5;

    public List<SugestaoCompraDTO> gerarRelatorioCompras() {
        List<Produto> produtos = produtoRepository.findAll();
        List<SugestaoCompraDTO> sugestoes = new ArrayList<>();

        for (Produto p : produtos) {
            // Filtros de produto
            if (!p.isAtivo()) continue;

            p.atualizarSaldoTotal();
            p.recalcularEstoqueMinimoSugerido();

            // Ignora produtos sem média de venda definida ou zerada
            if (p.getVendaMediaDiaria() == null || p.getVendaMediaDiaria().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Lógica de Ruptura (Se estoque atual <= mínimo)
            // Obs: Garante que o estoqueMinimo não seja nulo (padrão 0)
            int min = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;
            if (p.getQuantidadeEmEstoque() <= min) {
                sugestoes.add(calcularSugestao(p));
            }
        }

        // Ordena por urgência (String comparison).
        // OBS: "CRÍTICO" vem depois de "ALERTA" alfabeticamente, então invertemos (b compare a)
        // Adicionei verificação de null para evitar crash na linha 48
        sugestoes.sort((a, b) -> {
            String urgenciaA = a.getNivelUrgencia() != null ? a.getNivelUrgencia() : "";
            String urgenciaB = b.getNivelUrgencia() != null ? b.getNivelUrgencia() : "";
            return urgenciaB.compareTo(urgenciaA);
        });

        return sugestoes;
    }

    private SugestaoCompraDTO calcularSugestao(Produto p) {
        int diasReposicao = p.getDiasParaReposicao() != null ? p.getDiasParaReposicao() : 7; // Default 7 dias
        int diasCoberturaAlvo = diasReposicao + DIAS_MARGEM_SEGURANCA;

        BigDecimal vendaDiaria = p.getVendaMediaDiaria();

        int estoqueAlvo = vendaDiaria.multiply(new BigDecimal(diasCoberturaAlvo)).intValue();
        int quantidadeComprar = estoqueAlvo - p.getQuantidadeEmEstoque();

        if (quantidadeComprar <= 0) quantidadeComprar = 6; // Mínimo de reposição padrão (ex: meia dúzia)

        // Definir Urgência
        String urgencia = "NORMAL";
        int diasRestantesEstoque = 0;
        if (vendaDiaria.compareTo(BigDecimal.ZERO) > 0) {
            diasRestantesEstoque = new BigDecimal(p.getQuantidadeEmEstoque())
                    .divide(vendaDiaria, 0, BigDecimal.ROUND_DOWN).intValue();
        }

        if (p.getQuantidadeEmEstoque() <= 0) {
            urgencia = "Z-CRÍTICO (RUPTURA)"; // Z para ficar no topo da lista alfabética reversa
        } else if (diasRestantesEstoque < diasReposicao) {
            urgencia = "Y-ALERTA (VAI FALTAR)";
        }

        // Custo estimado (Usa preço de custo ou 0 se nulo)
        BigDecimal custoUnitario = p.getPrecoCusto() != null ? p.getPrecoCusto() : BigDecimal.ZERO;
        BigDecimal custoTotal = custoUnitario.multiply(new BigDecimal(quantidadeComprar));

        // Preenche o DTO com os novos campos
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