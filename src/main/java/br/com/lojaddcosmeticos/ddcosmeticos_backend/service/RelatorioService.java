package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RelatorioService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private ContaReceberRepository contaReceberRepository;

    // =========================================================================
    // 1. BI COMERCIAL (VENDAS)
    // =========================================================================

    @Transactional(readOnly = true)
    public RelatorioVendasDTO gerarRelatorioVendas(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();

        BigDecimal totalFaturado = nvl(vendaRepository.somarFaturamento(dataInicio, dataFim));
        Long totalVendasCount = nvl(vendaRepository.contarVendasNoPeriodo(dataInicio, dataFim));
        BigDecimal lucroReal = nvl(vendaRepository.calcularLucroBrutoNoPeriodo(dataInicio, dataFim));

        BigDecimal ticketMedio = (totalVendasCount > 0)
                ? totalFaturado.divide(new BigDecimal(totalVendasCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<VendaDiariaDTO> vendasDiarias = vendaRepository.agruparVendasPorDia(dataInicio, dataFim);
        List<VendaPorPagamentoDTO> porPagamento = vendaRepository.agruparPorFormaPagamento(dataInicio, dataFim);
        List<ProdutoRankingDTO> rankingMarcas = vendaRepository.buscarRankingMarcas(dataInicio, dataFim, PageRequest.of(0, 5));
        List<ProdutoRankingDTO> porCategoria = vendaRepository.buscarRankingCategorias(dataInicio, dataFim, PageRequest.of(0, 5));

        List<TicketRangeDTO> distribuicao = new ArrayList<>();
        distribuicao.add(new TicketRangeDTO("0-50", nvl(vendaRepository.contarVendasNaFaixa(dataInicio, dataFim, 0, 50))));
        distribuicao.add(new TicketRangeDTO("51-100", nvl(vendaRepository.contarVendasNaFaixa(dataInicio, dataFim, 51, 100))));
        distribuicao.add(new TicketRangeDTO("101-200", nvl(vendaRepository.contarVendasNaFaixa(dataInicio, dataFim, 101, 200))));
        distribuicao.add(new TicketRangeDTO("201+", nvl(vendaRepository.contarVendasNaFaixa(dataInicio, dataFim, 201, 999999))));

        List<CrossSellDTO> crossSell = vendaRepository.buscarCrossSell(dataInicio, dataFim, PageRequest.of(0, 3));

        return RelatorioVendasDTO.builder()
                .dataGeracao(LocalDateTime.now())
                .totalFaturado(totalFaturado)
                .quantidadeVendas(totalVendasCount.intValue())
                .ticketMedio(ticketMedio)
                .lucroBrutoEstimado(lucroReal)
                .vendasDiarias(vendasDiarias)
                .porPagamento(porPagamento)
                .rankingMarcas(rankingMarcas)
                .porCategoria(porCategoria)
                .distribuicaoTicket(distribuicao)
                .crossSell(crossSell)
                .build();
    }

    // =========================================================================
    // 2. BI DE ESTOQUE (INTELIGÊNCIA DE PORTFÓLIO)
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> gerarRelatorioEstoque(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDate.now().minusDays(30).atStartOfDay();
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();

        List<Produto> todosProdutos = produtoRepository.findAll();

        BigDecimal custoEstoque = todosProdutos.stream()
                .filter(Produto::isAtivo)
                .map(p -> nvl(p.getPrecoCusto()).multiply(new BigDecimal(nvl(p.getQuantidadeEmEstoque()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal vendaProjetada = todosProdutos.stream()
                .filter(Produto::isAtivo)
                .map(p -> nvl(p.getPrecoVenda()).multiply(new BigDecimal(nvl(p.getQuantidadeEmEstoque()))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long mixProdutos = todosProdutos.stream().filter(Produto::isAtivo).count();
        long produtosZerados = todosProdutos.stream().filter(p -> p.isAtivo() && nvl(p.getQuantidadeEmEstoque()) <= 0).count();

        double ruptura = mixProdutos > 0 ? ((double) produtosZerados / mixProdutos) * 100 : 0.0;

        List<ProdutoRankingDTO> rankingVendas = vendaRepository.buscarRankingProdutos(dataInicio, dataFim, PageRequest.of(0, 10));

        BigDecimal totalReceitaPeriodo = rankingVendas.stream()
                .map(ProdutoRankingDTO::valorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> curvaABC = new ArrayList<>();
        BigDecimal receitaAcumulada = BigDecimal.ZERO;

        for (ProdutoRankingDTO item : rankingVendas) {
            BigDecimal percentual = totalReceitaPeriodo.compareTo(BigDecimal.ZERO) > 0
                    ? item.valorTotal().divide(totalReceitaPeriodo, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;

            receitaAcumulada = receitaAcumulada.add(percentual);

            curvaABC.add(Map.of(
                    "name", item.produto().length() > 15 ? item.produto().substring(0, 15) + "..." : item.produto(),
                    "produtos", percentual,
                    "receita", percentual
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("custoEstoque", custoEstoque);
        response.put("vendaProjetada", vendaProjetada);
        response.put("mixProdutos", mixProdutos);
        response.put("ruptura", String.format(Locale.US, "%.1f", ruptura));
        response.put("curvaABC", curvaABC);

        response.put("aging", List.of(
                Map.of("name", "Giro Rápido (0-30d)", "value", custoEstoque.multiply(new BigDecimal("0.60"))),
                Map.of("name", "Atenção (31-90d)", "value", custoEstoque.multiply(new BigDecimal("0.25"))),
                Map.of("name", "Parado (+90d)", "value", custoEstoque.multiply(new BigDecimal("0.15")))
        ));

        response.put("cobertura", List.of(
                Map.of("name", "Cabelos", "dias", 45),
                Map.of("name", "Maquiagem", "dias", 28),
                Map.of("name", "Skin Care", "dias", 60)
        ));

        return response;
    }

    // =========================================================================
    // 3. BI FINANCEIRO (DRE E FLUXO)
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> gerarRelatorioFinanceiro(LocalDate inicio, LocalDate fim) {
        LocalDate hoje = LocalDate.now();
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : hoje.withDayOfMonth(1).atStartOfDay();
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : hoje.atTime(LocalTime.MAX);

        BigDecimal totalFaturado = nvl(vendaRepository.somarFaturamento(dataInicio, dataFim));
        BigDecimal custoMercadoria = nvl(vendaRepository.calcularLucroBrutoNoPeriodo(dataInicio, dataFim));
        BigDecimal cmvReal = totalFaturado.subtract(custoMercadoria);

        BigDecimal pagarPeriodo = nvl(contaPagarRepository.sumValorByDataVencimentoAndStatus(hoje, StatusConta.PENDENTE));
        BigDecimal vencidoPagar = nvl(contaPagarRepository.sumValorByDataVencimentoBeforeAndStatus(hoje, StatusConta.PENDENTE));
        BigDecimal receberPeriodo = nvl(contaReceberRepository.sumValorByDataVencimento(hoje));

        List<Map<String, Object>> dre = new ArrayList<>();
        dre.add(Map.of("name", "Receita Bruta", "valor", totalFaturado));
        dre.add(Map.of("name", "Custos (CMV)", "valor", cmvReal));
        dre.add(Map.of("name", "Despesas Op.", "valor", pagarPeriodo));
        dre.add(Map.of("name", "Resultado", "valor", totalFaturado.subtract(cmvReal).subtract(pagarPeriodo)));

        Map<String, Object> response = new HashMap<>();
        response.put("saldo", receberPeriodo.add(totalFaturado).subtract(pagarPeriodo));
        response.put("aPagar", pagarPeriodo);
        response.put("aReceber", receberPeriodo);
        response.put("vencido", vencidoPagar);
        response.put("dre", dre);

        return response;
    }

    // =========================================================================
    // 4. BI FISCAL (SIMPLES NACIONAL E CONFORMIDADE)
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> gerarRelatorioFiscal(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();

        BigDecimal faturamentoPeriodo = nvl(vendaRepository.somarFaturamento(dataInicio, dataFim));

        double aliquota = 4.0;
        if (faturamentoPeriodo.compareTo(new BigDecimal("15000")) > 0) aliquota = 7.3;
        if (faturamentoPeriodo.compareTo(new BigDecimal("30000")) > 0) aliquota = 9.5;

        Long produtosSemNcmCest = nvl(produtoRepository.contarProdutosSemFiscal());

        BigDecimal receitaMonofasica = faturamentoPeriodo.multiply(new BigDecimal("0.60"));
        BigDecimal receitaTributada = faturamentoPeriodo.subtract(receitaMonofasica);
        BigDecimal recuperacao = receitaMonofasica.multiply(new BigDecimal("0.02"));

        Map<String, Object> response = new HashMap<>();
        response.put("recuperacao", recuperacao);
        response.put("aliquota", aliquota);
        response.put("erros", produtosSemNcmCest);

        response.put("segregacao", List.of(
                Map.of("name", "Monofásico", "value", receitaMonofasica),
                Map.of("name", "Tributado Integral", "value", receitaTributada)
        ));

        return response;
    }

    // =========================================================================
    // 5. GERAÇÃO DE PDF E ETIQUETAS
    // =========================================================================

    public byte[] gerarPdfAuditoria(String search, String inicioStr, String fimStr) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Paragraph titulo = new Paragraph("RELATÓRIO DE AUDITORIA", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);
            document.add(new Paragraph(" "));

            final LocalDateTime dataInicioFilter = (inicioStr != null && !inicioStr.isEmpty())
                    ? LocalDate.parse(inicioStr).atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
            final LocalDateTime dataFimFilter = (fimStr != null && !fimStr.isEmpty())
                    ? LocalDate.parse(fimStr).atTime(LocalTime.MAX) : LocalDateTime.now().plusDays(1);

            String termo = (search != null) ? search.toLowerCase() : "";

            List<Auditoria> logs = auditoriaRepository.findAllByOrderByDataHoraDesc().stream()
                    .filter(a -> a.getDataHora().isAfter(dataInicioFilter) && a.getDataHora().isBefore(dataFimFilter))
                    .filter(a -> termo.isEmpty() ||
                            (a.getUsuarioResponsavel() != null && a.getUsuarioResponsavel().toLowerCase().contains(termo)) ||
                            (a.getMensagem() != null && a.getMensagem().toLowerCase().contains(termo)))
                    .limit(500)
                    .collect(Collectors.toList());

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.5f, 2f, 2.5f, 4f});

            String[] headers = {"Data/Hora", "Usuário", "Evento", "Descrição"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
                cell.setBackgroundColor(new Color(15, 23, 42));
                cell.setPadding(6);
                table.addCell(cell);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
            for (Auditoria logItem : logs) {
                table.addCell(criarCelula(logItem.getDataHora().format(dtf), Element.ALIGN_CENTER));
                table.addCell(criarCelula(logItem.getUsuarioResponsavel(), Element.ALIGN_CENTER));
                table.addCell(criarCelula(logItem.getTipoEvento().toString(), Element.ALIGN_CENTER));
                table.addCell(criarCelula(logItem.getMensagem(), Element.ALIGN_LEFT));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erro PDF Auditoria: ", e);
            return new byte[0];
        }
    }

    public byte[] gerarPdfSugestaoCompras(List<SugestaoCompraDTO> sugestoes) {
        return new byte[0];
    }

    public String gerarEtiquetaTermica(Produto p) {
        return String.format(
                "================================\n" +
                        "      DD COSMETICOS\n" +
                        "================================\n\n" +
                        "%s\n\n" +
                        "R$ %.2f\n\n" +
                        "COD: %s\n\n\n\n",
                p.getDescricao() != null ? (p.getDescricao().length() > 30 ? p.getDescricao().substring(0, 30) : p.getDescricao()) : "PRODUTO",
                p.getPrecoVenda() != null ? p.getPrecoVenda() : BigDecimal.ZERO,
                p.getCodigoBarras()
        );
    }

    private PdfPCell criarCelula(String texto, int alinhamento) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setHorizontalAlignment(alinhamento);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }

    private BigDecimal nvl(BigDecimal val) { return val == null ? BigDecimal.ZERO : val; }
    private Long nvl(Long val) { return val == null ? 0L : val; }
    private Integer nvl(Integer val) { return val == null ? 0 : val; }
}