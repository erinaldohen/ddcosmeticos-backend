package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoInventarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ItemVendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventarioInteligenteService {

    private final ProdutoRepository produtoRepository;
    private final ItemVendaRepository itemVendaRepository;

    public List<ProdutoInventarioDTO> gerarRelatorioInteligente() {
        List<Produto> todosProdutos = produtoRepository.findAllByAtivoTrue();

        LocalDateTime trintaDiasAtras = LocalDateTime.now().minusDays(30);
        LocalDateTime sessentaDiasAtras = LocalDateTime.now().minusDays(60);

        Map<Long, BigDecimal> receitaPorProduto = new HashMap<>();
        Map<Long, Integer> qtdVendida30Dias = new HashMap<>();
        Map<Long, Integer> qtdVendidaMesAnterior = new HashMap<>();

        // ✅ OTIMIZADO: Em vez de puxar milhares de ItemVenda (Objetos) para a RAM,
        // pedimos ao banco (PostgreSQL/H2) para fazer a soma e entregar-nos apenas um Long por produto.
        // Custo de memória cai de 150MB para ~200KB.
        for (Produto p : todosProdutos) {
            Long pId = p.getId();

            // Soma de quantidades via JPQL otimizada que nós criamos
            Long vendidou30 = itemVendaRepository.somarQuantidadeVendidaNoPeriodo(pId, trintaDiasAtras, LocalDateTime.now());
            Long vendidou60 = itemVendaRepository.somarQuantidadeVendidaNoPeriodo(pId, sessentaDiasAtras, trintaDiasAtras);

            int qtd30 = vendidou30 != null ? vendidou30.intValue() : 0;
            int qtd60 = vendidou60 != null ? vendidou60.intValue() : 0;

            // Receita calculada com o preço de venda atual (simplificação do Dashboard ABC)
            BigDecimal precoAtual = p.getPrecoVenda() != null ? p.getPrecoVenda() : BigDecimal.ZERO;
            BigDecimal receitaItem = precoAtual.multiply(new BigDecimal(qtd30));

            receitaPorProduto.put(pId, receitaItem);
            qtdVendida30Dias.put(pId, qtd30);
            qtdVendidaMesAnterior.put(pId, qtd60);
        }

        BigDecimal receitaTotalLoja = receitaPorProduto.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map.Entry<Long, BigDecimal>> listaOrdenadaReceita = receitaPorProduto.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .toList();

        Map<Long, String> classificacaoABC = new HashMap<>();
        BigDecimal receitaAcumulada = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> entry : listaOrdenadaReceita) {
            receitaAcumulada = receitaAcumulada.add(entry.getValue());
            double percentualAcumulado = receitaTotalLoja.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                    receitaAcumulada.divide(receitaTotalLoja, 4, java.math.RoundingMode.HALF_UP).doubleValue();

            if (percentualAcumulado <= 0.80) {
                classificacaoABC.put(entry.getKey(), "A");
            } else if (percentualAcumulado <= 0.95) {
                classificacaoABC.put(entry.getKey(), "B");
            } else {
                classificacaoABC.put(entry.getKey(), "C");
            }
        }

        List<ProdutoInventarioDTO> inventarioInteligente = new ArrayList<>();

        for (Produto p : todosProdutos) {
            Long id = p.getId();
            String curva = classificacaoABC.getOrDefault(id, "C");

            int qtd30 = qtdVendida30Dias.getOrDefault(id, 0);
            int qtd60 = qtdVendidaMesAnterior.getOrDefault(id, 0);

            double giroDiario = qtd30 / 30.0;

            String tendencia = "ESTAVEL";
            if (qtd30 > qtd60 * 1.2) tendencia = "ALTA";
            else if (qtd30 < qtd60 * 0.8) tendencia = "QUEDA";

            int diasCobertura = curva.equals("A") ? 20 : 10;
            int sugestao = (int) Math.ceil((giroDiario * diasCobertura) + (p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0) - p.getQuantidadeEmEstoque());

            if (sugestao < 0) sugestao = 0;

            inventarioInteligente.add(new ProdutoInventarioDTO(p, curva, giroDiario, tendencia, sugestao));
        }

        return inventarioInteligente;
    }
}