package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private ItemVendaRepository itemVendaRepository;

     /**
     * Relatório de produtos monofásicos (Isenção de PIS/COFINS).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> gerarRelatorioMonofasicos() {
        return produtoRepository.findAllByAtivoTrue().stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("codigo", p.getCodigoBarras());
            map.put("produto", p.getDescricao());
            map.put("ncm", p.getNcm());
            map.put("status", p.isMonofasico() ? "MONOFÁSICO (ISENTO)" : "TRIBUTADO NORMAL");
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Inventário de Estoque para contabilidade ou conferência física.
     */
    @Transactional(readOnly = true)
    public InventarioResponseDTO gerarInventarioEstoque(boolean contabil) {
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue();

        if (contabil) {
            produtos = produtos.stream().filter(Produto::isPossuiNfEntrada).collect(Collectors.toList());
        }

        List<ItemInventarioDTO> itensDTO = produtos.stream()
                .map(p -> ItemInventarioDTO.builder()
                        .codigoBarras(p.getCodigoBarras())
                        .descricao(p.getDescricao())
                        .unidade(p.getUnidade())
                        .quantidade(p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : BigDecimal.ZERO)
                        .custoUnitarioPmp(p.getPrecoMedioPonderado() != null ? p.getPrecoMedioPonderado() : BigDecimal.ZERO)
                        .valorTotalEstoque(p.getQuantidadeEmEstoque().multiply(p.getPrecoMedioPonderado()))
                        .statusFiscal(p.isPossuiNfEntrada() ? "FISCAL" : "GERENCIAL")
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalFinanceiro = itensDTO.stream().map(ItemInventarioDTO::valorTotalEstoque).reduce(BigDecimal.ZERO, BigDecimal::add);

        return InventarioResponseDTO.builder()
                .tipoInventario(contabil ? "CONTABIL" : "FISICO")
                .dataGeracao(LocalDateTime.now())
                .totalItens(itensDTO.size())
                .valorTotalEstoque(totalFinanceiro)
                .itens(itensDTO)
                .build();
    }

    /**
     * Curva ABC para identificação de produtos estratégicos.
     */
    @Transactional(readOnly = true)
    public List<ItemAbcDTO> gerarCurvaAbc() {
        List<ItemAbcDTO> listaBruta = itemVendaRepository.agruparVendasPorProduto();
        BigDecimal faturamentoTotal = listaBruta.stream().map(ItemAbcDTO::valorTotalVendido).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (faturamentoTotal.compareTo(BigDecimal.ZERO) == 0) return new ArrayList<>();

        List<ItemAbcDTO> listaEnriquecida = new ArrayList<>();
        BigDecimal acumuladoValor = BigDecimal.ZERO;

        for (ItemAbcDTO item : listaBruta) {
            acumuladoValor = acumuladoValor.add(item.valorTotalVendido());
            double percAcumulada = acumuladoValor.divide(faturamentoTotal, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();

            String classe = percAcumulada <= 80.0 ? "A" : (percAcumulada <= 95.0 ? "B" : "C");

            listaEnriquecida.add(new ItemAbcDTO(item.codigoBarras(), item.nomeProduto(), item.quantidadeVendida(),
                    item.valorTotalVendido(), 0.0, percAcumulada, classe));
        }
        return listaEnriquecida;
    }

    @Transactional(readOnly = true)
    public RelatorioVendasDTO gerarRelatorioVendas(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(LocalTime.MAX);

        List<Venda> vendas = vendaRepository.buscarPorPeriodo(dataInicio, dataFim);

        BigDecimal totalDescontos = BigDecimal.ZERO;
        BigDecimal totalLiquido = BigDecimal.ZERO;
        BigDecimal custoTotalMercadoria = BigDecimal.ZERO;

        for (Venda venda : vendas) {
            totalLiquido = totalLiquido.add(venda.getTotalVenda());
            totalDescontos = totalDescontos.add(venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO);

            for (ItemVenda item : venda.getItens()) {
                custoTotalMercadoria = custoTotalMercadoria.add(item.getCustoTotal() != null ? item.getCustoTotal() : BigDecimal.ZERO);
            }
        }

        BigDecimal totalBruto = totalLiquido.add(totalDescontos);
        BigDecimal lucroBruto = totalLiquido.subtract(custoTotalMercadoria);
        BigDecimal margem = totalLiquido.compareTo(BigDecimal.ZERO) > 0
                ? lucroBruto.divide(totalLiquido, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        // --- LÓGICA DO GRÁFICO POR HORA ---
        Map<Integer, List<Venda>> agrupadoPorHora = vendas.stream()
                .collect(Collectors.groupingBy(v -> v.getDataVenda().getHour()));

        List<VendasPorHoraDTO> vendasPorHora = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            List<Venda> vh = agrupadoPorHora.getOrDefault(h, new ArrayList<>());
            BigDecimal totalH = vh.stream().map(Venda::getTotalVenda).reduce(BigDecimal.ZERO, BigDecimal::add);
            vendasPorHora.add(new VendasPorHoraDTO(h, totalH, (long) vh.size()));
        }

        // RETORNO COM OS 10 PARÂMETROS SINCRONIZADOS
        return new RelatorioVendasDTO(
                inicio, fim, vendas.size(), totalBruto, totalDescontos,
                totalLiquido, custoTotalMercadoria, lucroBruto, margem,
                vendasPorHora // <--- A falta deste campo causava o erro na linha 33
        );
    }
}