package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoInventarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
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

        // Pega vendas dos últimos 30 e 60 dias para calcular tendência e curva ABC
        LocalDateTime trintaDiasAtras = LocalDateTime.now().minusDays(30);
        LocalDateTime sessentaDiasAtras = LocalDateTime.now().minusDays(60);

        List<ItemVenda> vendasUltimos30Dias = itemVendaRepository.findByVendaDataVendaAfter(trintaDiasAtras);
        List<ItemVenda> vendasMesAnterior = itemVendaRepository.findByVendaDataVendaBetween(sessentaDiasAtras, trintaDiasAtras);

        // 1. Calcular Receita e Quantidade por Produto (Últimos 30 dias)
        Map<Long, BigDecimal> receitaPorProduto = new HashMap<>();
        Map<Long, Integer> qtdVendida30Dias = new HashMap<>();

        for (ItemVenda item : vendasUltimos30Dias) {
            Long pId = item.getProduto().getId();

            // 🔥 CORREÇÃO: quantidade já é BigDecimal, não precisa de "new BigDecimal()"
            // Também adicionámos proteções contra nulos
            BigDecimal qtd = item.getQuantidade() != null ? item.getQuantidade() : BigDecimal.ZERO;
            BigDecimal preco = item.getPrecoUnitario() != null ? item.getPrecoUnitario() : BigDecimal.ZERO;
            BigDecimal desc = item.getDesconto() != null ? item.getDesconto() : BigDecimal.ZERO;

            BigDecimal receitaItem = preco.multiply(qtd).subtract(desc).max(BigDecimal.ZERO);

            receitaPorProduto.put(pId, receitaPorProduto.getOrDefault(pId, BigDecimal.ZERO).add(receitaItem));
            qtdVendida30Dias.put(pId, qtdVendida30Dias.getOrDefault(pId, 0) + qtd.intValue());
        }

        // 2. Calcular Vendas do Mês Anterior (para Tendência)
        Map<Long, Integer> qtdVendidaMesAnterior = new HashMap<>();
        for (ItemVenda item : vendasMesAnterior) {
            Long pId = item.getProduto().getId();
            BigDecimal qtd = item.getQuantidade() != null ? item.getQuantidade() : BigDecimal.ZERO;
            qtdVendidaMesAnterior.put(pId, qtdVendidaMesAnterior.getOrDefault(pId, 0) + qtd.intValue());
        }

        // 3. Ordenar para Curva ABC (80% A, 15% B, 5% C)
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
                classificacaoABC.put(entry.getKey(), "A"); // Trazem 80% do lucro
            } else if (percentualAcumulado <= 0.95) {
                classificacaoABC.put(entry.getKey(), "B"); // Trazem 15% do lucro
            } else {
                classificacaoABC.put(entry.getKey(), "C"); // Trazem 5% do lucro
            }
        }

        // 4. Montar o DTO final com as sugestões
        List<ProdutoInventarioDTO> inventarioInteligente = new ArrayList<>();

        for (Produto p : todosProdutos) {
            Long id = p.getId();
            String curva = classificacaoABC.getOrDefault(id, "C"); // Se não vendeu, é C

            int qtd30 = qtdVendida30Dias.getOrDefault(id, 0);
            int qtd60 = qtdVendidaMesAnterior.getOrDefault(id, 0);

            // Giro Diário
            double giroDiario = qtd30 / 30.0;

            // Tendência
            String tendencia = "ESTAVEL";
            if (qtd30 > qtd60 * 1.2) tendencia = "ALTA"; // Cresceu mais de 20%
            else if (qtd30 < qtd60 * 0.8) tendencia = "QUEDA"; // Caiu mais de 20%

            // Sugestão de Compra (Giro diário * dias de cobertura + Estoque Mínimo - Estoque Atual)
            int diasCobertura = curva.equals("A") ? 20 : 10; // Queremos mais estoque de produtos A
            int sugestao = (int) Math.ceil((giroDiario * diasCobertura) + (p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0) - p.getQuantidadeEmEstoque());

            if (sugestao < 0) sugestao = 0; // Não precisa comprar

            inventarioInteligente.add(new ProdutoInventarioDTO(p, curva, giroDiario, tendencia, sugestao));
        }

        return inventarioInteligente;
    }
}