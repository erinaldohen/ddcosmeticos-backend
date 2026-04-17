package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AuditoriaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.DashboardResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.dashboard.FiscalDashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
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

    private final EntityManager entityManager;
    private final ProdutoRepository produtoRepository;
    private final ConfiguracaoLojaRepository configuracaoRepository;
    private final ItemVendaRepository itemVendaRepository;

    @Transactional(readOnly = true)
    public Long contarProdutosPendentesDeRevisao() {
        try {
            return produtoRepository.countProdutosPendentesDeRevisao();
        } catch (Exception e) {
            return 0L;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obterDadosDoDashboard(String periodo) {
        LocalDateTime agora = LocalDateTime.now();

        LocalDateTime inicioRef = agora.toLocalDate().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fimRef = agora.toLocalDate().withDayOfMonth(agora.toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);

        if ("hoje".equalsIgnoreCase(periodo)) {
            inicioRef = agora.toLocalDate().atStartOfDay();
            fimRef = agora.toLocalDate().atTime(LocalTime.MAX);
        } else if ("mes_passado".equalsIgnoreCase(periodo)) {
            inicioRef = agora.minusMonths(1).toLocalDate().withDayOfMonth(1).atStartOfDay();
            fimRef = agora.minusMonths(1).toLocalDate().withDayOfMonth(agora.minusMonths(1).toLocalDate().lengthOfMonth()).atTime(LocalTime.MAX);
        }

        ConfiguracaoLoja config = null;
        try {
            config = configuracaoRepository.findAll().stream().findFirst().orElse(null);
        } catch (Exception e) { log.warn("Aviso ao ler Configurações."); }

        BigDecimal metaMensal = BigDecimal.ZERO;
        BigDecimal metaDiariaConfig = BigDecimal.ZERO;

        if (config != null) {
            if (config.getVendas() != null && config.getVendas().getMetaMensal() != null) {
                metaMensal = config.getVendas().getMetaMensal();
            }
            if (config.getFinanceiro() != null && config.getFinanceiro().getMetaDiaria() != null) {
                metaDiariaConfig = config.getFinanceiro().getMetaDiaria();
            }
        }

        // ==============================================================================
        // EXTRAÇÃO DE DADOS
        // ==============================================================================

        BigDecimal faturamentoPeriodo = BigDecimal.ZERO;
        BigDecimal descontosPeriodo = BigDecimal.ZERO;
        long quantidadeVendas = 0L;
        Map<Integer, Integer> agrupadoPorHora = new HashMap<>();
        Map<String, BigDecimal> mapaDias = new TreeMap<>();
        DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("dd/MM");

        List<Object[]> vendasRaw = entityManager.createQuery(
                        "SELECT v.valorTotal, v.descontoTotal, v.dataVenda FROM Venda v " +
                                "WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> :statusCancelada", Object[].class)
                .setParameter("inicio", inicioRef)
                .setParameter("fim", fimRef)
                .setParameter("statusCancelada", StatusFiscal.CANCELADA)
                .getResultList();

        quantidadeVendas = vendasRaw.size();
        for (Object[] obj : vendasRaw) {
            BigDecimal totalVenda = obj[0] != null ? new BigDecimal(obj[0].toString()) : BigDecimal.ZERO;
            BigDecimal desconto = obj[1] != null ? new BigDecimal(obj[1].toString()) : BigDecimal.ZERO;
            LocalDateTime dt = (LocalDateTime) obj[2];

            faturamentoPeriodo = faturamentoPeriodo.add(totalVenda);
            descontosPeriodo = descontosPeriodo.add(desconto);

            if (dt != null) {
                agrupadoPorHora.merge(dt.getHour(), 1, Integer::sum);
                mapaDias.merge(dt.format(fmtDia), totalVenda, BigDecimal::add);
            }
        }

        List<Map<String, Object>> formasPagamentoList = new ArrayList<>();
        List<Object[]> pagamentosRaw = entityManager.createQuery(
                        "SELECT p.formaPagamento, SUM(p.valor) FROM PagamentoVenda p JOIN p.venda v " +
                                "WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> :statusCancelada GROUP BY p.formaPagamento", Object[].class)
                .setParameter("inicio", inicioRef)
                .setParameter("fim", fimRef)
                .setParameter("statusCancelada", StatusFiscal.CANCELADA)
                .getResultList();

        for (Object[] obj : pagamentosRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", obj[0] != null ? obj[0].toString().replace("_", " ") : "OUTROS");
            m.put("value", obj[1] != null ? new BigDecimal(obj[1].toString()) : BigDecimal.ZERO);
            formasPagamentoList.add(m);
        }

        BigDecimal custoTotalPeriodo = BigDecimal.ZERO;
        long totalLinhasItensPeriodo = 0L;
        Map<String, BigDecimal> faturamentoPorCategoria = new HashMap<>();
        Map<String, BigDecimal> faturamentoPorProduto = new HashMap<>();

        List<Object[]> itensRaw = entityManager.createQuery(
                        "SELECT p.categoria, p.descricao, SUM(i.precoUnitario * i.quantidade), " +
                                "SUM(COALESCE(i.custoUnitarioHistorico, p.precoCusto) * i.quantidade), COUNT(i) " +
                                "FROM ItemVenda i JOIN i.produto p JOIN i.venda v " +
                                "WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> :statusCancelada " +
                                "GROUP BY p.categoria, p.descricao", Object[].class)
                .setParameter("inicio", inicioRef)
                .setParameter("fim", fimRef)
                .setParameter("statusCancelada", StatusFiscal.CANCELADA)
                .getResultList();

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

        List<Map<String, Object>> performanceVendedores = new ArrayList<>();
        List<Object[]> vendRaw = entityManager.createQuery(
                        "SELECT u.nome, COUNT(DISTINCT v.idVenda), SUM(v.valorTotal) FROM Venda v JOIN v.usuario u " +
                                "WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> :statusCancelada GROUP BY u.nome", Object[].class)
                .setParameter("inicio", inicioRef)
                .setParameter("fim", fimRef)
                .setParameter("statusCancelada", StatusFiscal.CANCELADA)
                .getResultList();

        for (Object[] obj : vendRaw) {
            Map<String, Object> m = new HashMap<>();
            m.put("nome", obj[0] != null ? obj[0].toString() : "Usuário");
            m.put("converteu", obj[1] != null ? ((Number) obj[1]).longValue() : 0L);
            m.put("vendas", obj[2] != null ? new BigDecimal(obj[2].toString()) : BigDecimal.ZERO);
            performanceVendedores.add(m);
        }
        performanceVendedores.sort((a, b) -> ((BigDecimal) b.get("vendas")).compareTo((BigDecimal) a.get("vendas")));

        LocalDate diaAtual = agora.toLocalDate();
        LocalDate daquiA7Dias = diaAtual.plusDays(7);

        BigDecimal totalDespesas = safeQueryVal(
                "SELECT SUM(c.valorTotal) FROM ContaPagar c WHERE c.status <> :status AND c.dataVencimento BETWEEN :h AND :d",
                diaAtual, daquiA7Dias, StatusConta.PAGO);

        Map<String, Object> fluxo7Dias = new HashMap<>();
        fluxo7Dias.put("receitasPrevistas", faturamentoPeriodo);
        fluxo7Dias.put("despesasPrevistas", totalDespesas);
        fluxo7Dias.put("saldoProjetado", faturamentoPeriodo.subtract(totalDespesas));

        // ==============================================================================
        // FORMATAR E CALCULAR KPI'S
        // ==============================================================================
        BigDecimal ticketMedio = quantidadeVendas > 0 ? faturamentoPeriodo.divide(new BigDecimal(quantidadeVendas), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        double mixProdutos = quantidadeVendas > 0 ? (double) totalLinhasItensPeriodo / quantidadeVendas : 0.0;
        BigDecimal impostoMes = faturamentoPeriodo.multiply(new BigDecimal("0.04"));

        List<Map<String, Object>> topCategorias = faturamentoPorCategoria.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(5)
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("nome", e.getKey()); m.put("valor", e.getValue()); return m; })
                .collect(Collectors.toList());

        List<Map<String, Object>> topProdutosList = faturamentoPorProduto.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())).limit(10)
                .map(e -> { Map<String, Object> m = new HashMap<>(); m.put("nome", e.getKey()); m.put("valor", e.getValue()); return m; })
                .collect(Collectors.toList());

        List<Map<String, Object>> vendasPorHora = new ArrayList<>();
        for (int h = 8; h <= 20; h++) {
            Map<String, Object> m = new HashMap<>();
            m.put("hora", String.format("%02dh", h));
            m.put("qtd", agrupadoPorHora.getOrDefault(h, 0));
            vendasPorHora.add(m);
        }

        List<Map<String, Object>> graficoVendas = mapaDias.entrySet().stream().map(e -> {
            Map<String, Object> m = new HashMap<>(); m.put("data", e.getKey()); m.put("total", e.getValue()); return m;
        }).collect(Collectors.toList());

        Map<String, Object> inventarioNode = new HashMap<>();
        inventarioNode.put("indiceRuptura", "0.0");
        Map<String, Object> riscoVencMap = new HashMap<>(); riscoVencMap.put("itens", 0); riscoVencMap.put("valorRisco", BigDecimal.ZERO); inventarioNode.put("produtosVencendo", riscoVencMap);
        Map<String, Object> riscoCMap = new HashMap<>(); riscoCMap.put("itens", 0); riscoCMap.put("valorImobilizado", BigDecimal.ZERO); inventarioNode.put("estoqueCurvaC", riscoCMap);
        Map<String, Object> vendasPerdidas = new HashMap<>(); vendasPerdidas.put("quantidade", 0); vendasPerdidas.put("valorEstimado", BigDecimal.ZERO); inventarioNode.put("vendasPerdidas", vendasPerdidas);

        int diasPassados = Math.max(1, ("hoje".equals(periodo) ? 1 : agora.getDayOfMonth()));
        int diasNoMes = YearMonth.from(inicioRef).lengthOfMonth();
        BigDecimal runRate = faturamentoPeriodo.divide(new BigDecimal(diasPassados), 2, RoundingMode.HALF_UP).multiply(new BigDecimal(diasNoMes));

        // 🔥 O VERDADEIRO MOTOR DO IMPACTO DA IA
        BigDecimal roiValor = BigDecimal.ZERO;
        Long roiItens = 0L;
        try {
            roiValor = itemVendaRepository.calcularRoiIAValor(inicioRef);
            roiItens = itemVendaRepository.calcularRoiIAItens(inicioRef);
        } catch (Exception e) {
            log.error("Erro ao calcular ROI da IA: {}", e.getMessage());
        }

        Map<String, Object> inteligenciaNode = new HashMap<>();
        inteligenciaNode.put("roiValor", roiValor != null ? roiValor : BigDecimal.ZERO);
        inteligenciaNode.put("roiItens", roiItens != null ? roiItens : 0L);

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
        financeiro.put("fluxoCaixa7Dias", fluxo7Dias);
        financeiro.put("crescimentoMoM", 0.0);
        financeiro.put("runRate", runRate);

        Map<String, Object> response = new HashMap<>();
        response.put("financeiro", financeiro);
        response.put("topCategorias", topCategorias);
        response.put("topProdutos", topProdutosList);
        response.put("performanceVendedores", performanceVendedores);
        response.put("inventario", inventarioNode);
        response.put("inteligencia", inteligenciaNode); // 🔥 NÓ DA IA ADICIONADO AO RESPONSE

        int diasNoMesFim = diasNoMes;
        response.put("metaDiaria", metaDiariaConfig.compareTo(BigDecimal.ZERO) > 0 ? metaDiariaConfig : metaMensal.divide(new BigDecimal(diasNoMesFim), 2, RoundingMode.HALF_UP));
        response.put("metaMensal", metaMensal);

        return response;
    }

    private BigDecimal safeQueryVal(String hql, LocalDate inicio, LocalDate fim, Object statusObj) {
        try {
            BigDecimal res = entityManager.createQuery(hql, BigDecimal.class)
                    .setParameter("h", inicio)
                    .setParameter("d", fim)
                    .setParameter("status", statusObj)
                    .getSingleResult();
            return res != null ? res : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // Mock Methods para compatibilidade de Interfaces do Controller
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obterListaRisco(String tipo) { return new ArrayList<>(); }

    @Transactional(readOnly = true)
    public DashboardDTO carregarDashboard() { return null; }

    @Transactional(readOnly = true)
    public FiscalDashboardDTO getResumoFiscal(LocalDate inicio, LocalDate fim) { return new FiscalDashboardDTO(0L, 0L, 0L); }

    @Transactional(readOnly = true)
    public DashboardResumoDTO obterResumoGeral() { return DashboardResumoDTO.builder().build(); }

    @Transactional(readOnly = true)
    public List<AuditoriaRequestDTO> buscarAlertasRecentes() { return Collections.emptyList(); }
}