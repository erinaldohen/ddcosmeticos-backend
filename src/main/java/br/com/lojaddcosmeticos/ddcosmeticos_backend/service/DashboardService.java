package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final EntityManager em;
    private final ProdutoRepository produtoRepository;
    private final VendaRepository vendaRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final VendaPerdidaRepository vendaPerdidaRepository;
    private final ConfiguracaoLojaRepository configuracaoRepository;

    @Transactional(readOnly = true)
    public Long contarProdutosPendentesDeRevisao() {
        try {
            return produtoRepository.countByRevisaoPendenteTrueAndAtivoTrue();
        } catch (Exception e) {
            return 0L;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obterDadosDoDashboard(String periodo) {
        LocalDateTime agora = LocalDateTime.now();

        // 1. FILTRO TEMPORAL BLINDADO
        LocalDateTime inicioRef = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimRef = agora.toLocalDate().withDayOfMonth(agora.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);

        if ("hoje".equalsIgnoreCase(periodo)) {
            inicioRef = agora.toLocalDate().atStartOfDay();
            fimRef = agora.toLocalDate().atTime(LocalTime.MAX);
        } else if ("mes_passado".equalsIgnoreCase(periodo)) {
            inicioRef = agora.minusMonths(1).toLocalDate().withDayOfMonth(1).atStartOfDay();
            fimRef = agora.minusMonths(1).toLocalDate().withDayOfMonth(agora.minusMonths(1).toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);
        }

        // 2. CONFIGURAÇÕES DA LOJA
        ConfiguracaoLoja config = configuracaoRepository.findAll().stream().findFirst().orElse(null);
        BigDecimal metaMensal = BigDecimal.valueOf(50000.00);
        if (config != null && config.getVendas() != null && config.getVendas().getMetaMensal() != null) {
            metaMensal = config.getVendas().getMetaMensal();
        }

        // ==============================================================================
        // 🚀 OTIMIZAÇÃO: CONSULTAS HQL EXTREMAMENTE RÁPIDAS E SEGURAS
        // ==============================================================================

        // A. Dados Base
        List<Venda> vendasDoPeriodo = vendaRepository.buscarVendasPorPeriodo(inicioRef, fimRef);
        BigDecimal faturamentoPeriodo = BigDecimal.ZERO;
        BigDecimal descontosPeriodo = BigDecimal.ZERO;
        Map<Integer, Integer> agrupadoPorHora = new HashMap<>();
        Map<String, BigDecimal> mapaDias = new TreeMap<>();
        DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("dd/MM");

        for (Venda v : vendasDoPeriodo) {
            BigDecimal totalVenda = safeBigDecimal(v.getValorTotal());
            faturamentoPeriodo = faturamentoPeriodo.add(totalVenda);
            descontosPeriodo = descontosPeriodo.add(safeBigDecimal(v.getDescontoTotal()));

            if (v.getDataVenda() != null) {
                agrupadoPorHora.merge(v.getDataVenda().getHour(), 1, Integer::sum);
                mapaDias.merge(v.getDataVenda().format(fmtDia), totalVenda, BigDecimal::add);
            }
        }

        // B. Formas de Pagamento
        List<Object[]> pagamentosRaw = em.createQuery(
                        "SELECT p.formaPagamento, SUM(p.valor) FROM PagamentoVenda p JOIN p.venda v " +
                                "WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> :statusCancelada GROUP BY p.formaPagamento", Object[].class)
                .setParameter("inicio", inicioRef)
                .setParameter("fim", fimRef)
                .setParameter("statusCancelada", StatusFiscal.CANCELADA)
                .getResultList();

        List<Map<String, Object>> formasPagamentoList = new ArrayList<>();
        for (Object[] obj : pagamentosRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", obj[0] != null ? obj[0].toString().replace("_", " ") : "OUTROS");
            m.put("value", obj[1] != null ? new BigDecimal(obj[1].toString()) : BigDecimal.ZERO);
            formasPagamentoList.add(m);
        }

        // C. Curva ABC e Ticket Médio (Sem N+1)
        List<Object[]> itensRaw = em.createQuery(
                        "SELECT p.categoria, p.descricao, SUM(i.precoUnitario * i.quantidade), " +
                                "SUM(COALESCE(i.custoUnitarioHistorico, p.precoCusto) * i.quantidade), COUNT(i.id) " +
                                "FROM ItemVenda i JOIN i.produto p JOIN i.venda v " +
                                "WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> :statusCancelada " +
                                "GROUP BY p.categoria, p.descricao", Object[].class)
                .setParameter("inicio", inicioRef)
                .setParameter("fim", fimRef)
                .setParameter("statusCancelada", StatusFiscal.CANCELADA)
                .getResultList();

        BigDecimal custoTotalPeriodo = BigDecimal.ZERO;
        long totalLinhasItensPeriodo = 0L;
        Map<String, BigDecimal> faturamentoPorCategoria = new HashMap<>();
        Map<String, BigDecimal> faturamentoPorProduto = new HashMap<>();

        for (Object[] obj : itensRaw) {
            String cat = obj[0] != null ? obj[0].toString() : "Diversos";
            String prodDesc = obj[1] != null ? obj[1].toString() : "Item Avulso";
            BigDecimal fat = obj[2] != null ? new BigDecimal(obj[2].toString()) : BigDecimal.ZERO;
            BigDecimal custo = obj[3] != null ? new BigDecimal(obj[3].toString()) : BigDecimal.ZERO;
            long linhas = obj[4] != null ? ((Number) obj[4]).longValue() : 0L;

            custoTotalPeriodo = custoTotalPeriodo.add(custo);
            totalLinhasItensPeriodo += linhas;
            faturamentoPorCategoria.merge(cat, fat, BigDecimal::add);
            faturamentoPorProduto.merge(prodDesc, fat, BigDecimal::add);
        }

        // D. Vendedores
        List<Object[]> vendRaw = em.createQuery(
                        "SELECT u.nome, COUNT(DISTINCT v.idVenda), SUM(v.valorTotal) FROM Venda v JOIN v.usuario u " +
                                "WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> :statusCancelada GROUP BY u.nome", Object[].class)
                .setParameter("inicio", inicioRef)
                .setParameter("fim", fimRef)
                .setParameter("statusCancelada", StatusFiscal.CANCELADA)
                .getResultList();

        List<Map<String, Object>> performanceVendedores = new ArrayList<>();
        for (Object[] obj : vendRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("nome", obj[0] != null ? obj[0].toString() : "Usuário");
            m.put("converteu", obj[1] != null ? ((Number) obj[1]).longValue() : 0L);
            m.put("vendas", obj[2] != null ? new BigDecimal(obj[2].toString()) : BigDecimal.ZERO);
            performanceVendedores.add(m);
        }
        performanceVendedores.sort((a, b) -> ((BigDecimal) b.get("vendas")).compareTo((BigDecimal) a.get("vendas")));

        // E. Contas a Pagar/Receber 7 Dias
        LocalDate diaAtual = agora.toLocalDate();
        LocalDate daquiA7Dias = diaAtual.plusDays(7);

        BigDecimal totalDespesas = safeQueryVal(
                "SELECT SUM(c.valorTotal) FROM ContaPagar c WHERE c.status <> :status AND c.dataVencimento BETWEEN :h AND :d",
                diaAtual, daquiA7Dias, StatusConta.PAGO);

        BigDecimal totalReceitas = safeQueryVal(
                "SELECT SUM(c.valor) FROM ContaReceber c WHERE c.status <> :status AND c.dataVencimento BETWEEN :h AND :d",
                diaAtual, daquiA7Dias, StatusConta.PAGO);

        Map<String, Object> fluxo7Dias = new HashMap<>();
        fluxo7Dias.put("receitasPrevistas", totalReceitas);
        fluxo7Dias.put("despesasPrevistas", totalDespesas);
        fluxo7Dias.put("saldoProjetado", totalReceitas.subtract(totalDespesas));

        // ==============================================================================
        // FORMATAR PARA O FRONTEND (Zero Objetos Complexos ou Proxies)
        // ==============================================================================
        long quantidadeVendas = vendasDoPeriodo.size();
        BigDecimal ticketMedio = quantidadeVendas > 0 ? faturamentoPeriodo.divide(new BigDecimal(quantidadeVendas), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        double mixProdutos = quantidadeVendas > 0 ? (double) totalLinhasItensPeriodo / quantidadeVendas : 0.0;
        BigDecimal impostoMes = faturamentoPeriodo.multiply(new BigDecimal("0.04"));

        List<Map<String, Object>> topCategorias = faturamentoPorCategoria.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(5)
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("nome", e.getKey()); m.put("valor", e.getValue()); return m; })
                .collect(Collectors.toList());

        // 🚨 RANKING ABC EM MEMÓRIA (Evita chamadas paginadas ao banco)
        List<Map<String, Object>> topProdutosList = faturamentoPorProduto.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(10)
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("nome", e.getKey()); m.put("valor", e.getValue()); return m; })
                .collect(Collectors.toList());

        List<Map<String, Object>> vendasPorHora = new ArrayList<>();
        for (int h = 8; h <= 20; h++) {
            Map<String, Object> m = new HashMap<>(); m.put("horaStr", String.format("%02dh", h)); m.put("qtd", agrupadoPorHora.getOrDefault(h, 0)); vendasPorHora.add(m);
        }

        List<Map<String, Object>> graficoVendas = mapaDias.entrySet().stream().map(e -> {
            Map<String, Object> m = new HashMap<>(); m.put("data", e.getKey()); m.put("total", e.getValue()); return m;
        }).collect(Collectors.toList());

        ProdutoRepository.RiscoEstoqueProjection projVencendo = produtoRepository.calcularRiscoVencimento(LocalDate.now().plusDays(60));
        ProdutoRepository.RiscoEstoqueProjection projCurvaC = produtoRepository.calcularEstoqueParado(LocalDate.now().minusDays(90));

        Long qtdVendasPerdidas = vendaPerdidaRepository.contarRupturasNoPeriodo(inicioRef, fimRef);
        Map<String, Object> vendasPerdidas = new HashMap<>();
        vendasPerdidas.put("quantidade", qtdVendasPerdidas != null ? qtdVendasPerdidas : 0);
        vendasPerdidas.put("valorEstimado", ticketMedio.multiply(new BigDecimal(qtdVendasPerdidas != null ? qtdVendasPerdidas : 0)));

        Map<String, Object> inventarioNode = new HashMap<>();
        inventarioNode.put("indiceRuptura", calcularRuptura());
        inventarioNode.put("produtosVencendo", Map.of("itens", projVencendo != null ? projVencendo.getItens() : 0, "valorRisco", projVencendo != null ? safeBigDecimal(projVencendo.getValorRisco()) : BigDecimal.ZERO));
        inventarioNode.put("estoqueCurvaC", Map.of("itens", projCurvaC != null ? projCurvaC.getItens() : 0, "valorImobilizado", projCurvaC != null ? safeBigDecimal(projCurvaC.getValorRisco()) : BigDecimal.ZERO));
        inventarioNode.put("vendasPerdidas", vendasPerdidas);

        Long totalTicketsID = vendaRepository.contarVendasIdentificadasNoMes(inicioRef);
        Long ticketsRecorrentes = vendaRepository.contarVendasRecorrentesNoMes(inicioRef);
        double taxaRecorrencia = (totalTicketsID != null && totalTicketsID > 0) ? ((double) (ticketsRecorrentes != null ? ticketsRecorrentes : 0) / totalTicketsID) * 100 : 0.0;

        List<Map<String, Object>> origemVendas = new ArrayList<>();
        try {
            origemVendas = vendaRepository.somarFaturamentoPorOrigemMes(inicioRef).stream().map(proj -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", proj.getCanalOrigem() != null ? proj.getCanalOrigem().name().replace("_", " ") : "Loja Física");
                m.put("value", proj.getTotal() != null ? proj.getTotal() : BigDecimal.ZERO); return m;
            }).collect(Collectors.toList());
        } catch (Exception e) { log.warn("Aviso na origem de vendas."); }

        int diasPassados = Math.max(1, ("hoje".equals(periodo) ? 1 : agora.getDayOfMonth()));
        int diasNoMes = YearMonth.from(inicioRef).lengthOfMonth();
        BigDecimal runRate = faturamentoPeriodo.divide(new BigDecimal(diasPassados), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(diasNoMes));

        BigDecimal faturamentoMesAnterior = safeBigDecimal(vendaRepository.somarFaturamento(inicioRef.minusMonths(1), fimRef.minusMonths(1)));
        double crescimentoMoM = faturamentoMesAnterior.compareTo(BigDecimal.ZERO) > 0 ? faturamentoPeriodo.subtract(faturamentoMesAnterior).divide(faturamentoMesAnterior, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue() : 100.0;

        Map<String, Object> inteligenciaVendas = new HashMap<>();
        inteligenciaVendas.put("roiValor", safeBigDecimal(vendaRepository.calcularFaturamentoInfluenciaIA(inicioRef, fimRef)));
        inteligenciaVendas.put("roiItens", vendaRepository.contarItensInfluenciaIA(inicioRef, fimRef) != null ? vendaRepository.contarItensInfluenciaIA(inicioRef, fimRef) : 0L);
        // 🚨 REMOVIDO "afinidadeProdutos" (O Proxy que causava o Erro 500 no Jackson)

        Map<String, Object> financeiro = new HashMap<>();
        financeiro.put("faturamentoHoje", faturamentoPeriodo);
        financeiro.put("descontosHoje", descontosPeriodo);
        financeiro.put("custoTotalReposicaoHoje", custoTotalPeriodo);
        financeiro.put("lucroBrutoHoje", faturamentoPeriodo.subtract(custoTotalPeriodo));
        financeiro.put("vendasHoje", quantidadeVendas);
        financeiro.put("ticketMedio", ticketMedio);
        financeiro.put("produtosDistintosPorVenda", mixProdutos);
        financeiro.put("impostoProvisorioMes", impostoMes);
        financeiro.put("formasPagamento", formasPagamentoList);
        financeiro.put("vendasPorHora", vendasPorHora);
        financeiro.put("graficoVendas", graficoVendas);
        financeiro.put("origemVendas", origemVendas);
        financeiro.put("taxaRecorrencia", taxaRecorrencia);
        financeiro.put("fluxoCaixa7Dias", fluxo7Dias);
        financeiro.put("crescimentoMoM", crescimentoMoM);
        financeiro.put("runRate", runRate);

        Map<String, Object> response = new HashMap<>();
        response.put("financeiro", financeiro);
        response.put("topCategorias", topCategorias);
        response.put("topProdutos", topProdutosList);
        response.put("performanceVendedores", performanceVendedores);
        response.put("inventario", inventarioNode);
        response.put("metaDiaria", metaMensal.divide(new BigDecimal(diasNoMes), 2, RoundingMode.HALF_UP));
        response.put("metaMensal", metaMensal);
        response.put("inteligencia", inteligenciaVendas);

        return response;
    }

    private BigDecimal safeQueryVal(String hql, LocalDate inicio, LocalDate fim, Object statusObj) {
        try {
            BigDecimal res = em.createQuery(hql, BigDecimal.class)
                    .setParameter("h", inicio)
                    .setParameter("d", fim)
                    .setParameter("status", statusObj)
                    .getSingleResult();
            return res != null ? res : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String calcularRuptura() {
        long total = produtoRepository.count();
        long zerados = produtoRepository.countByQuantidadeEmEstoqueLessThanEqualAndAtivoTrue(0);
        return total > 0 ? String.format(Locale.US, "%.1f", ((double) zerados / total) * 100) : "0.0";
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> obterListaRisco(String tipo) {
        List<Map<String, Object>> lista = new ArrayList<>();
        if ("vencimento".equals(tipo)) {
            lista.add(Map.of("produto", "Sérum Facial Anti-Idade", "estoque", 12, "custo", "R$ 450,00"));
            lista.add(Map.of("produto", "Protetor Solar FPS 50", "estoque", 8, "custo", "R$ 160,00"));
        } else {
            lista.add(Map.of("produto", "Paleta Sombras Inverno", "estoque", 25, "custo", "R$ 1.125,00"));
            lista.add(Map.of("produto", "Perfume Floral Genérico", "estoque", 14, "custo", "R$ 840,00"));
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public DashboardDTO carregarDashboard() { return null; }
    @Transactional(readOnly = true)
    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) { return new FiscalDashboardDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, Collections.emptyList(), Collections.emptyList()); }
    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoGeral() { return DashboardResumoDTO.builder().build(); }
    @Transactional(readOnly = true)
    public List<AuditoriaRequestDTO> buscarAlertasRecentes() { return Collections.emptyList(); }
    private BigDecimal safeBigDecimal(BigDecimal valor) { return valor != null ? valor : BigDecimal.ZERO; }
}