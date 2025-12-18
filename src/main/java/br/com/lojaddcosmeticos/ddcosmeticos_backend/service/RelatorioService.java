package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
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

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private ItemVendaRepository itemVendaRepository;

    @Autowired
    private ContaReceberRepository contaReceberRepository;

    @Transactional(readOnly = true)
    public RelatorioVendasDTO gerarRelatorioVendas(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(LocalTime.MAX);

        List<Venda> vendas = vendaRepository.buscarPorPeriodo(dataInicio, dataFim);

        BigDecimal totalBruto = BigDecimal.ZERO;
        BigDecimal totalDescontos = BigDecimal.ZERO;
        BigDecimal totalLiquido = BigDecimal.ZERO;
        BigDecimal custoTotalMercadoria = BigDecimal.ZERO;

        for (Venda venda : vendas) {
            BigDecimal descontoVenda = venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO;
            totalLiquido = totalLiquido.add(venda.getTotalVenda());
            totalDescontos = totalDescontos.add(descontoVenda);

            for (ItemVenda item : venda.getItens()) {
                totalBruto = totalBruto.add(item.getValorTotalItem());
                BigDecimal custoItem = item.getCustoTotal() != null ? item.getCustoTotal() : BigDecimal.ZERO;
                custoTotalMercadoria = custoTotalMercadoria.add(custoItem);
            }
        }

        totalBruto = totalLiquido.add(totalDescontos);

        BigDecimal lucroBruto = totalLiquido.subtract(custoTotalMercadoria);
        BigDecimal margem = BigDecimal.ZERO;

        if (totalLiquido.compareTo(BigDecimal.ZERO) > 0) {
            margem = lucroBruto.divide(totalLiquido, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        return new RelatorioVendasDTO(inicio, fim, vendas.size(), totalBruto, totalDescontos, totalLiquido, custoTotalMercadoria, lucroBruto, margem);
    }

    @Transactional(readOnly = true)
    public List<Auditoria> buscarHistoricoAjustesEstoque() {
        return auditoriaRepository.findAll().stream()
                .filter(a -> a.getTipoEvento() != null &&
                        (a.getTipoEvento().startsWith("INVENTARIO_") || a.getTipoEvento().equals("ESTOQUE_ENTRADA")))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> gerarRelatorioMonofasicos() {
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue();
        return produtos.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("codigo", p.getCodigoBarras());
            map.put("produto", p.getDescricao());
            map.put("ncm", p.getNcm());
            map.put("monofasico", p.isMonofasico());
            map.put("status", p.isMonofasico() ? "ISENTO DE PIS/COFINS (MONOFÁSICO)" : "TRIBUTADO NORMAL");
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InventarioResponseDTO gerarInventarioEstoque(boolean contabil) {
        // Busca todos os produtos ativos
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue();

        // Filtra para inventário fiscal se solicitado
        if (contabil) {
            produtos = produtos.stream()
                    .filter(Produto::isPossuiNfEntrada)
                    .collect(Collectors.toList());
        }

        // Mapeamento detalhado de cada produto para o DTO de item do inventário
        List<ItemInventarioDTO> itensDTO = produtos.stream()
                .map(p -> {
                    BigDecimal qtd = p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : BigDecimal.ZERO;
                    BigDecimal custo = p.getPrecoMedioPonderado() != null ? p.getPrecoMedioPonderado() : BigDecimal.ZERO;

                    return ItemInventarioDTO.builder()
                            .codigoBarras(p.getCodigoBarras())
                            .descricao(p.getDescricao())
                            .unidade(p.getUnidade()) // AGORA USA O CAMPO DA ENTIDADE
                            .quantidade(qtd)
                            .custoUnitarioPmp(custo)
                            .valorTotalEstoque(qtd.multiply(custo))
                            .statusFiscal(p.isPossuiNfEntrada() ? "FISCAL" : "NAO_FISCAL")
                            .build();
                })
                .collect(Collectors.toList());

        // Soma o valor total financeiro baseado nos itens mapeados
        BigDecimal valorTotalEstoque = itensDTO.stream()
                .map(ItemInventarioDTO::valorTotalEstoque)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Retorno utilizando o Builder para evitar erros de ordem de parâmetros
        return InventarioResponseDTO.builder()
                .tipoInventario(contabil ? "INVENTARIO_FISCAL_CONTABIL" : "INVENTARIO_GERENCIAL_FISICO")
                .dataGeracao(LocalDateTime.now())
                .totalItens(itensDTO.size())
                .valorTotalEstoque(valorTotalEstoque)
                .itens(itensDTO)
                .build();
    }

    public List<RelatorioPerdasDTO> gerarRelatorioPerdasPorMotivo() {
        return new ArrayList<>();
    }

    @Transactional(readOnly = true)
    public List<ItemAbcDTO> gerarCurvaAbc() {
        // 1. Busca os dados agrupados e ordenados pelo banco
        List<ItemAbcDTO> listaBruta = itemVendaRepository.agruparVendasPorProduto();

        // 2. Calcula o Faturamento Total para base de porcentagem
        BigDecimal faturamentoTotal = listaBruta.stream()
                .map(ItemAbcDTO::valorTotalVendido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (faturamentoTotal.compareTo(BigDecimal.ZERO) == 0) return new ArrayList<>();

        List<ItemAbcDTO> listaEnriquecida = new ArrayList<>();
        BigDecimal acumuladoValor = BigDecimal.ZERO;

        // 3. Processamento da Curva ABC (Princípio de Pareto)
        for (ItemAbcDTO item : listaBruta) {
            // Calcula % individual deste item no faturamento
            double porcentagem = item.valorTotalVendido()
                    .divide(faturamentoTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).doubleValue();

            acumuladoValor = acumuladoValor.add(item.valorTotalVendido());

            // Calcula % acumulada
            double porcentagemAcumulada = acumuladoValor
                    .divide(faturamentoTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).doubleValue();

            // 4. Classificação de Curva
            String classe;
            if (porcentagemAcumulada <= 80.0) {
                classe = "A"; // 80% do faturamento
            } else if (porcentagemAcumulada <= 95.0) {
                classe = "B"; // Próximos 15%
            } else {
                classe = "C"; // Últimos 5%
            }

            // Cria novo Record enriquecido com os cálculos
            listaEnriquecida.add(new ItemAbcDTO(
                    item.codigoBarras(),
                    item.nomeProduto(),
                    item.quantidadeVendida(),
                    item.valorTotalVendido(),
                    porcentagem,
                    porcentagemAcumulada,
                    classe
            ));
        }

        return listaEnriquecida;
    }

    @Transactional(readOnly = true)
    public FechoCaixaDTO gerarFechoCaixa(LocalDate data) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.atTime(LocalTime.MAX);

        // 1. Busca resumo de vendas do repositório
        List<Venda> vendasDoDia = vendaRepository.buscarPorPeriodo(inicio, fim);

        BigDecimal bruto = vendasDoDia.stream()
                .map(Venda::getTotalVenda)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Busca detalhamento por forma de pagamento no financeiro
        List<ResumoPagamentoDTO> pagamentos = contaReceberRepository.agruparPagamentosPorData(data);

        return FechoCaixaDTO.builder()
                .data(data)
                .totalVendas(vendasDoDia.size())
                .faturamentoBruto(bruto)
                .totalDescontos(BigDecimal.ZERO) // Pode ser expandido futuramente
                .faturamentoLiquido(bruto)
                .pagamentos(pagamentos)
                .build();
    }
}