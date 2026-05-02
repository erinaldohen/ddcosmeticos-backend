package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ComissaoVendedorDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioComissaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
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
    @Autowired private ConfiguracaoLojaRepository configuracaoRepository; // Alterado para bater com o padrão
    @Autowired private CaixaDiarioRepository caixaRepository;
    @Autowired private EmailService emailService;

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

        // NOTA DBA: Numa loja gigante (> 50k produtos), este findAll() deverá ser convertido
        // para JPQL (ex: SELECT SUM(p.precoCusto * p.quantidadeEmEstoque) FROM Produto p).
        // Para a realidade atual de varejo padrão, a stream lida bem.
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

        BigDecimal pagarPeriodo = nvl(contaPagarRepository.somarValorPorVencimentoEStatus(hoje, StatusConta.PENDENTE));
        BigDecimal vencidoPagar = nvl(contaPagarRepository.somarTotalVencido(hoje));
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

        Long produtosSemNcmCest = produtoRepository.findAll().stream()
                .filter(p -> p.isAtivo() && (p.getNcm() == null || p.getNcm().isBlank()))
                .count();

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
    // 5. BI COMISSÕES DE VENDEDORES (NOVO & BLINDADO)
    // =========================================================================

    @Transactional(readOnly = true)
    public RelatorioComissaoDTO gerarRelatorioComissoes(LocalDateTime inicio, LocalDateTime fim, Long vendedorId) {

        ConfiguracaoLoja config = configuracaoRepository.findById(1L).orElse(null);

        BigDecimal percentualComissao = BigDecimal.ZERO;
        boolean sobreLucro = true;
        boolean descontarTaxas = false;

        BigDecimal taxaCredito = BigDecimal.ZERO;
        BigDecimal taxaDebito = BigDecimal.ZERO;

        if (config != null && config.getComissoes() != null) {
            percentualComissao = nvl(config.getComissoes().getPercentualGeral());
            sobreLucro = "LUCRO".equalsIgnoreCase(config.getComissoes().getComissionarSobre());
            descontarTaxas = Boolean.TRUE.equals(config.getComissoes().getDescontarTaxasCartao());

            if (config.getFinanceiro() != null) {
                taxaCredito = nvl(config.getFinanceiro().getTaxaCredito());
                taxaDebito = nvl(config.getFinanceiro().getTaxaDebito()); // 🚨 Bate na taxa correta de débito!
            }
        }

        List<Venda> vendas;
        if (vendedorId != null) {
            vendas = vendaRepository.findByDataVendaBetweenAndUsuarioIdAndStatusNfce(inicio, fim, vendedorId, StatusFiscal.AUTORIZADA);
        } else {
            vendas = vendaRepository.findByDataVendaBetweenAndStatusNfce(inicio, fim, StatusFiscal.AUTORIZADA);
        }

        Map<Long, ComissaoVendedorDTO> mapaComissoes = new HashMap<>();
        RelatorioComissaoDTO relatorio = new RelatorioComissaoDTO();
        relatorio.setDataInicio(inicio.toLocalDate());
        relatorio.setDataFim(fim.toLocalDate());

        for (Venda venda : vendas) {
            Usuario vendedor = venda.getVendedor();
            if (vendedor == null) continue;

            ComissaoVendedorDTO dtoVendedor = mapaComissoes.computeIfAbsent(vendedor.getId(),
                    id -> new ComissaoVendedorDTO(id, vendedor.getNome(), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

            BigDecimal valorBase = sobreLucro
                    ? nvl(venda.getValorTotal()).subtract(nvl(venda.getCustoTotal()))
                    : nvl(venda.getValorTotal());

            if (valorBase.compareTo(BigDecimal.ZERO) < 0) valorBase = BigDecimal.ZERO;

            // 🚨 CORREÇÃO CRÍTICA FINANCEIRA: Aplica a taxa correta baseada no método de pagamento
            if (descontarTaxas && venda.getFormaDePagamento() != null) {
                String formaPgto = venda.getFormaDePagamento().name();
                BigDecimal taxaAplicar = BigDecimal.ZERO;

                if (formaPgto.contains("CREDITO")) {
                    taxaAplicar = taxaCredito;
                } else if (formaPgto.contains("DEBITO")) {
                    taxaAplicar = taxaDebito;
                }

                if (taxaAplicar.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal valorDesconto = valorBase.multiply(taxaAplicar).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    valorBase = valorBase.subtract(valorDesconto);
                }
            }

            BigDecimal comissaoDaVenda = valorBase.multiply(percentualComissao).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            dtoVendedor.setQuantidadeVendas(dtoVendedor.getQuantidadeVendas() + 1);
            dtoVendedor.setValorTotalVendido(dtoVendedor.getValorTotalVendido().add(nvl(venda.getValorTotal())));
            dtoVendedor.setValorBaseComissao(dtoVendedor.getValorBaseComissao().add(valorBase));
            dtoVendedor.setValorComissao(dtoVendedor.getValorComissao().add(comissaoDaVenda));

            relatorio.setTotalVendidoGeral(relatorio.getTotalVendidoGeral().add(nvl(venda.getValorTotal())));
            relatorio.setTotalComissoesGeral(relatorio.getTotalComissoesGeral().add(comissaoDaVenda));
        }

        relatorio.getVendedores().addAll(mapaComissoes.values());
        relatorio.getVendedores().sort((v1, v2) -> v2.getValorTotalVendido().compareTo(v1.getValorTotalVendido()));

        return relatorio;
    }

    // =========================================================================
    // 6. GERAÇÃO DE PDF E ETIQUETAS
    // =========================================================================

    @Transactional(readOnly = true)
    public byte[] gerarPdfAuditoria(String search, String inicioStr, String fimStr) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            final LocalDateTime dIni = (inicioStr != null && !inicioStr.isEmpty()) ? LocalDate.parse(inicioStr).atStartOfDay() : LocalDateTime.now().minusDays(30);
            final LocalDateTime dFim = (fimStr != null && !fimStr.isEmpty()) ? LocalDate.parse(fimStr).atTime(LocalTime.MAX) : LocalDateTime.now();

            // ✅ CORREÇÃO: Utiliza o método seguro com limite de datas para evitar colapso do servidor
            List<Auditoria> logs = auditoriaRepository.findByDataHoraBetweenOrderByDataHoraDesc(dIni, dFim).stream()
                    .filter(a -> search == null || search.isEmpty() || a.getMensagem().toLowerCase().contains(search.toLowerCase()))
                    .limit(300) // Proteção adicional OOM
                    .collect(Collectors.toList());

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.addCell("Data"); table.addCell("Usuário"); table.addCell("Evento"); table.addCell("Mensagem");

            for (Auditoria logItem : logs) {
                table.addCell(logItem.getDataHora().toString());
                table.addCell(logItem.getUsuarioResponsavel());
                table.addCell(logItem.getTipoEvento().name());
                table.addCell(logItem.getMensagem());
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) { return new byte[0]; }
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

    public byte[] gerarPdfComissoes(RelatorioComissaoDTO dados) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fontSub = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

            Paragraph titulo = new Paragraph("FECHAMENTO DE COMISSÕES - DD COSMÉTICOS", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            Paragraph periodo = new Paragraph(String.format("Período: %s até %s",
                    dados.getDataInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    dados.getDataFim().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))), fontSub);
            periodo.setAlignment(Element.ALIGN_CENTER);
            document.add(periodo);
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 2f, 2.5f, 2.5f});

            String[] headers = {"Vendedor", "Vendas", "Total Vendido", "Comissão"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, fontHeader));
                cell.setBackgroundColor(new Color(15, 23, 42));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(8);
                table.addCell(cell);
            }

            for (ComissaoVendedorDTO v : dados.getVendedores()) {
                table.addCell(criarCelula(v.getNomeVendedor(), Element.ALIGN_LEFT));
                table.addCell(criarCelula(v.getQuantidadeVendas().toString(), Element.ALIGN_CENTER));
                table.addCell(criarCelula("R$ " + v.getValorTotalVendido().setScale(2, RoundingMode.HALF_UP), Element.ALIGN_RIGHT));
                table.addCell(criarCelula("R$ " + v.getValorComissao().setScale(2, RoundingMode.HALF_UP), Element.ALIGN_RIGHT));
            }
            document.add(table);

            document.add(new Paragraph(" "));
            Paragraph total = new Paragraph(String.format("Total Geral de Comissões: R$ %s",
                    dados.getTotalComissoesGeral().setScale(2, RoundingMode.HALF_UP)), fontTitulo);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.add(new Paragraph("\n\n\n\n"));
            Paragraph pAssinatura = new Paragraph("__________________________________________\nAssinatura do Responsável", fontSub);
            pAssinatura.setAlignment(Element.ALIGN_CENTER);
            document.add(pAssinatura);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF de Comissões: ", e);
            return new byte[0];
        }
    }

    // =========================================================================
    // 7. DOSSIÊ EXECUTIVO 360º COM "MOTOR DE IA" HEURÍSTICO
    // =========================================================================
    public byte[] gerarDossieExecutivoPdf(LocalDate inicio, LocalDate fim) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, Color.DARK_GRAY);
            Font fontSecao = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(236, 72, 153));
            Font fontCorpo = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
            Font fontIA = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, new Color(59, 130, 246));

            RelatorioVendasDTO vendas = gerarRelatorioVendas(inicio, fim);
            Map<String, Object> financeiro = gerarRelatorioFinanceiro(inicio, fim);
            Map<String, Object> estoque = gerarRelatorioEstoque(inicio, fim);
            Map<String, Object> fiscal = gerarRelatorioFiscal(inicio, fim);

            BigDecimal fatBruto = vendas.getTotalFaturado();
            BigDecimal lucroBruto = vendas.getLucroBrutoEstimado();
            BigDecimal despPagar = (BigDecimal) financeiro.get("aPagar");
            BigDecimal ebitda = fatBruto.subtract(fatBruto.subtract(lucroBruto)).subtract(despPagar);
            BigDecimal custoEst = (BigDecimal) estoque.get("custoEstoque");
            BigDecimal gmroi = custoEst.compareTo(BigDecimal.ZERO) > 0 ? lucroBruto.divide(custoEst, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            Long errosFiscais = (Long) fiscal.get("erros");

            Paragraph titulo = new Paragraph("DOSSIÊ EXECUTIVO 360º", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            Paragraph subtitulo = new Paragraph("DD Cosméticos - Análise Estratégica Interdepartamental\nPeríodo: " +
                    inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " a " +
                    fim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "\n\n", fontCorpo);
            subtitulo.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitulo);

            document.add(new Paragraph("RESUMO EXECUTIVO (SÍNTESE IA)", fontSecao));

            StringBuilder analiseIA = new StringBuilder();
            analiseIA.append("A operação encontra-se num ciclo ").append(ebitda.compareTo(BigDecimal.ZERO) >= 0 ? "de expansão saudável." : "de atenção por consumo de caixa.");
            analiseIA.append(" O EBITDA no período foi de R$ ").append(ebitda).append(".");
            analiseIA.append(" A eficiência sobre o capital investido (GMROI) está em ").append(gmroi).append(" (para cada R$ 1 retido no estoque, a empresa gerou R$ ").append(gmroi).append(" de lucro bruto). ");

            if (errosFiscais > 0) {
                analiseIA.append("No campo Jurídico/Compliance, há um risco tributário eminente: ").append(errosFiscais).append(" produtos estão sem NCM/CEST, podendo gerar autuações fiscais.");
            } else {
                analiseIA.append("A conformidade Fiscal e Compliance operam a 100% de precisão.");
            }

            Paragraph parecerIA = new Paragraph("✨ " + analiseIA.toString() + "\n\n", fontIA);
            parecerIA.setSpacingAfter(15f);
            document.add(parecerIA);

            document.add(new Paragraph("1. DEPARTAMENTO FINANCEIRO E COMERCIAL", fontSecao));

            PdfPTable tableFin = new PdfPTable(2);
            tableFin.setWidthPercentage(100);
            tableFin.setSpacingBefore(10f);
            tableFin.setSpacingAfter(20f);

            tableFin.addCell(new PdfPCell(new Phrase("Faturamento Bruto", fontCorpo)));
            tableFin.addCell(new PdfPCell(new Phrase("R$ " + fatBruto.setScale(2, RoundingMode.HALF_UP), fontCorpo)));

            tableFin.addCell(new PdfPCell(new Phrase("Lucro Bruto (Margem de Contribuição)", fontCorpo)));
            tableFin.addCell(new PdfPCell(new Phrase("R$ " + lucroBruto.setScale(2, RoundingMode.HALF_UP), fontCorpo)));

            tableFin.addCell(new PdfPCell(new Phrase("Despesas Operacionais (A Pagar)", fontCorpo)));
            tableFin.addCell(new PdfPCell(new Phrase("R$ " + despPagar.setScale(2, RoundingMode.HALF_UP), fontCorpo)));

            tableFin.addCell(new PdfPCell(new Phrase("EBITDA (Caixa Operacional Livre)", fontCorpo)));
            PdfPCell cellEbitda = new PdfPCell(new Phrase("R$ " + ebitda.setScale(2, RoundingMode.HALF_UP), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            cellEbitda.setBackgroundColor(ebitda.compareTo(BigDecimal.ZERO) >= 0 ? new Color(220, 252, 231) : new Color(254, 226, 226));
            tableFin.addCell(cellEbitda);

            document.add(tableFin);

            document.add(new Paragraph("2. OPERAÇÕES, ESTOQUE E SUPPLY CHAIN", fontSecao));
            Paragraph txtOperacoes = new Paragraph("O departamento de compras possui atualmente R$ " + custoEst.setScale(2, RoundingMode.HALF_UP) + " imobilizados. " +
                    "A taxa de ruptura (falta de produto na prateleira) atual é de " + estoque.get("ruptura") + "% do mix ativo.\n\n", fontCorpo);
            document.add(txtOperacoes);

            document.add(new Paragraph("3. RECURSOS HUMANOS (PERFORMANCE DE VENDAS)", fontSecao));

            RelatorioComissaoDTO comissoes = gerarRelatorioComissoes(inicio.atStartOfDay(), fim.atTime(LocalTime.MAX), null);
            BigDecimal totalComissoes = comissoes.getTotalComissoesGeral();

            Paragraph txtRh = new Paragraph("Foram realizadas " + vendas.getQuantidadeVendas() + " operações de caixa. " +
                    "O custo com comissionamento da equipa (folha de incentivos) estimou-se em R$ " + totalComissoes.setScale(2, RoundingMode.HALF_UP) + " neste período. " +
                    "O Ticket Médio por cliente manteve-se em R$ " + vendas.getTicketMedio().setScale(2, RoundingMode.HALF_UP) + ".\n\n", fontCorpo);
            document.add(txtRh);

            document.add(new Paragraph("4. JURÍDICO, FISCAL E COMPLIANCE", fontSecao));
            Paragraph txtFiscal = new Paragraph("Alíquota média Simples Nacional projetada: " + fiscal.get("aliquota") + "%. " +
                    "A segregação de produtos monofásicos permitiu uma economia Lícita (Recuperação Tributária) estimada em R$ " +
                    ((BigDecimal) fiscal.get("recuperacao")).setScale(2, RoundingMode.HALF_UP) + ".\n", fontCorpo);
            document.add(txtFiscal);

            document.add(new Paragraph("\n\n"));
            Paragraph rodape = new Paragraph("Documento Confidencial DD Cosméticos. Gerado eletronicamente em " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ".", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
            rodape.setAlignment(Element.ALIGN_CENTER);
            document.add(rodape);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Falha ao montar o PDF do Dossiê: ", e);
            return null;
        }
    }

    // =========================================================================
    // 8. GERADOR DO BALANÇO TRIMESTRAL (EARNINGS RELEASE CORPORATIVO)
    // =========================================================================
    public byte[] gerarBalancoTrimestralPdf(int ano, int trimestre) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            LocalDate inicio;
            LocalDate fim;
            switch (trimestre) {
                case 1: inicio = LocalDate.of(ano, 1, 1); fim = LocalDate.of(ano, 3, 31); break;
                case 2: inicio = LocalDate.of(ano, 4, 1); fim = LocalDate.of(ano, 6, 30); break;
                case 3: inicio = LocalDate.of(ano, 7, 1); fim = LocalDate.of(ano, 9, 30); break;
                case 4: inicio = LocalDate.of(ano, 10, 1); fim = LocalDate.of(ano, 12, 31); break;
                default: throw new IllegalArgumentException("Trimestre inválido");
            }

            RelatorioVendasDTO vendas = gerarRelatorioVendas(inicio, fim);
            Map<String, Object> fin = gerarRelatorioFinanceiro(inicio, fim);
            Map<String, Object> est = gerarRelatorioEstoque(inicio, fim);

            BigDecimal fatBruto = vendas.getTotalFaturado();
            BigDecimal lucroBruto = vendas.getLucroBrutoEstimado();
            BigDecimal despOp = (BigDecimal) fin.get("aPagar");
            BigDecimal ebitda = fatBruto.subtract(fatBruto.subtract(lucroBruto)).subtract(despOp);
            BigDecimal custoEst = (BigDecimal) est.get("custoEstoque");
            BigDecimal gmroi = custoEst.compareTo(BigDecimal.ZERO) > 0 ? lucroBruto.divide(custoEst, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

            Font fTituloLogo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, new Color(236, 72, 153));
            Font fSub = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY);
            Font fSecao = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(15, 23, 42));
            Font fTexto = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);

            Paragraph logo = new Paragraph("DD COSMÉTICOS S/A", fTituloLogo);
            logo.setAlignment(Element.ALIGN_CENTER);
            document.add(logo);

            Paragraph release = new Paragraph("EARNINGS RELEASE | RESULTADOS DO " + trimestre + "T" + ano, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            release.setAlignment(Element.ALIGN_CENTER);
            release.setSpacingBefore(10);
            document.add(release);

            Paragraph dataBase = new Paragraph("Período base: " + inicio + " a " + fim, fSub);
            dataBase.setAlignment(Element.ALIGN_CENTER);
            dataBase.setSpacingAfter(30);
            document.add(dataBase);

            Paragraph p1 = new Paragraph("PARTE I: MENSAGEM DA ADMINISTRAÇÃO (SÍNTESE INTELIGENTE)", fSecao);
            p1.setSpacingAfter(10);
            document.add(p1);

            String txtFin = "Durante o " + trimestre + "º trimestre, a companhia atingiu um faturamento bruto de R$ " + fatBruto.setScale(2, RoundingMode.HALF_UP) + ". ";
            if (ebitda.compareTo(BigDecimal.ZERO) > 0) {
                txtFin += "O EBITDA ajustado foi positivo em R$ " + ebitda.setScale(2, RoundingMode.HALF_UP) + ", demonstrando excelente capacidade de geração de caixa pelas operações diárias. A estrutura de custos fixos está alinhada com as receitas.";
            } else {
                txtFin += "O EBITDA ajustado registou consumo de caixa na ordem de R$ " + ebitda.setScale(2, RoundingMode.HALF_UP) + ". Exige-se reavaliação imediata dos custos operacionais ou aumento do ticket médio para estancar o deficit operacional.";
            }
            document.add(new Paragraph("Desempenho Financeiro:\n" + txtFin, fTexto));
            document.add(new Paragraph(" "));

            String txtEst = "O Capital de Giro imobilizado em mercadorias fechou o trimestre em R$ " + custoEst.setScale(2, RoundingMode.HALF_UP) + ". ";
            if (gmroi.compareTo(new BigDecimal("1.5")) >= 0) {
                txtEst += "O indicador de retorno GMROI é de " + gmroi + ", o que indica altíssima eficiência. A cada R$ 1,00 estocado, a empresa gera lucros substanciais, provando forte aderência do mix de produtos junto ao consumidor.";
            } else {
                txtEst += "O indicador GMROI está em " + gmroi + ", abaixo do ideal histórico. Há excesso de estoque sem giro (Curva C) prendendo o caixa da companhia. Necessária política agressiva de liquidação de inventário antigo.";
            }
            document.add(new Paragraph("Eficiência de Capital e Supply Chain:\n" + txtEst, fTexto));
            document.add(new Paragraph(" "));

            document.add(Chunk.NEXTPAGE);

            Paragraph p2 = new Paragraph("PARTE II: INDICADORES FUNDAMENTALISTAS", fSecao);
            p2.setSpacingAfter(20);
            document.add(p2);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);

            PdfPCell h1 = new PdfPCell(new Phrase("Métrica Operacional", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE)));
            h1.setBackgroundColor(new Color(15, 23, 42)); h1.setPadding(8);
            PdfPCell h2 = new PdfPCell(new Phrase("Resultado Consolidado", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE)));
            h2.setBackgroundColor(new Color(15, 23, 42)); h2.setPadding(8); h2.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(h1); table.addCell(h2);

            addTableRow(table, "1. Faturamento Bruto", "R$ " + fatBruto.setScale(2, RoundingMode.HALF_UP));
            addTableRow(table, "2. Lucro Bruto (Margem)", "R$ " + lucroBruto.setScale(2, RoundingMode.HALF_UP));
            addTableRow(table, "3. Ticket Médio Trimestral", "R$ " + vendas.getTicketMedio().setScale(2, RoundingMode.HALF_UP));
            addTableRow(table, "4. Despesas Operacionais Realizadas", "R$ " + despOp.setScale(2, RoundingMode.HALF_UP));
            addTableRow(table, "5. EBITDA Operacional", "R$ " + ebitda.setScale(2, RoundingMode.HALF_UP));
            addTableRow(table, "6. Custo de Estoque Total", "R$ " + custoEst.setScale(2, RoundingMode.HALF_UP));
            addTableRow(table, "7. Ruptura de Estoque (Faltas)", est.get("ruptura") + "%");
            addTableRow(table, "8. GMROI (Retorno s/ Investimento)", gmroi.toString() + "x");

            document.add(table);

            document.add(new Paragraph("\n\n"));
            Paragraph legal = new Paragraph("DISCLAIMER: Este relatório contém projeções geradas por inteligência heurística baseadas em dados inseridos no sistema. Não serve como auditoria contábil externa com valor legal para a Receita Federal.", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.LIGHT_GRAY));
            legal.setAlignment(Element.ALIGN_JUSTIFIED);
            document.add(legal);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Falha ao gerar o Balanço Trimestral: ", e);
            return null;
        }
    }

    private void addTableRow(PdfPTable table, String label, String value) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA, 11)));
        c1.setPadding(8);
        PdfPCell c2 = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setPadding(8);
        table.addCell(c1);
        table.addCell(c2);
    }

    // =========================================================================
    // 9. RELATÓRIO MENSAL AUTOMATIZADO: CONSULTORIA IA & DOSSIÊ EXECUTIVO
    // =========================================================================

    @Scheduled(cron = "0 0 8 1 * *")
    public void dispararRelatorioMensalAgendado() {
        log.info("⏰ Iniciando geração automática do Relatório Mensal com Consultoria IA...");
        LocalDate mesAnterior = LocalDate.now().minusMonths(1);
        LocalDateTime inicio = mesAnterior.withDayOfMonth(1).atStartOfDay();
        LocalDateTime fim = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth()).atTime(LocalTime.MAX);
        String nomeMes = mesAnterior.format(DateTimeFormatter.ofPattern("MMMM/yyyy", new Locale("pt", "BR")));
        gerarEEnviarRelatorioMensal(inicio, fim, nomeMes.toUpperCase());
    }

    public void dispararRelatorioMensalTeste() {
        LocalDateTime inicio = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime fim = LocalDateTime.now();
        gerarEEnviarRelatorioMensal(inicio, fim, "TESTE ATUAL (" + inicio.format(DateTimeFormatter.ofPattern("MMM/yyyy", new Locale("pt", "BR"))) + ")");
    }

    private void gerarEEnviarRelatorioMensal(LocalDateTime inicio, LocalDateTime fim, String mesReferencia) {
        try {
            ConfiguracaoLoja config = configuracaoRepository.findById(1L).orElse(null); // Consistência de Configuração
            String emailAdmin = (config != null && config.getLoja() != null && config.getLoja().getEmail() != null && !config.getLoja().getEmail().isBlank())
                    ? config.getLoja().getEmail().trim() : "lojaddcosmeticos@gmail.com";

            RelatorioVendasDTO vendas = gerarRelatorioVendas(inicio.toLocalDate(), fim.toLocalDate());
            Map<String, Object> estoque = gerarRelatorioEstoque(inicio.toLocalDate(), fim.toLocalDate());
            Map<String, Object> fiscal = gerarRelatorioFiscal(inicio.toLocalDate(), fim.toLocalDate());

            BigDecimal fatBruto = nvl(vendas.getTotalFaturado());
            BigDecimal lucroBruto = nvl(vendas.getLucroBrutoEstimado());
            BigDecimal ticketMedio = nvl(vendas.getTicketMedio());
            String rupturaStr = estoque.get("ruptura") != null ? estoque.get("ruptura").toString().replace(",", ".") : "0.0";
            double ruptura = Double.parseDouble(rupturaStr);
            Long errosFiscais = fiscal.get("erros") != null ? (Long) fiscal.get("erros") : 0L;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 40, 40, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new Color(15, 23, 42));
            Font fSub = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY);
            Font fSecao = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(236, 72, 153));
            Font fNormal = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
            Font fIA = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 11, new Color(59, 130, 246));

            try {
                String nomeArquivoLogo = config != null && config.getLoja() != null ? config.getLoja().getLogoUrl() : null;

                if (nomeArquivoLogo != null && !nomeArquivoLogo.isBlank()) {
                    String caminhoFinal = nomeArquivoLogo.contains("/") ? nomeArquivoLogo : "uploads/" + nomeArquivoLogo;

                    Image logo = Image.getInstance(caminhoFinal);
                    logo.scaleToFit(120, 120);
                    logo.setAlignment(Element.ALIGN_CENTER);
                    document.add(logo);
                } else {
                    throw new RuntimeException("Logo não configurada no banco.");
                }
            } catch (Exception e) {
                log.warn("⚠️ Logo física não encontrada ou não configurada. Usando texto padrão no cabeçalho.");
                Paragraph logoText = new Paragraph("DD COSMÉTICOS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, new Color(236, 72, 153)));
                logoText.setAlignment(Element.ALIGN_CENTER);
                document.add(logoText);
            }

            Paragraph titulo = new Paragraph("DOSSIÊ MENSAL DE INTELIGÊNCIA", fTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingBefore(10);
            document.add(titulo);

            Paragraph periodo = new Paragraph("Período de Análise: " + mesReferencia, fSub);
            periodo.setAlignment(Element.ALIGN_CENTER);
            periodo.setSpacingAfter(30);
            document.add(periodo);

            document.add(new Paragraph("1. PERFORMANCE FINANCEIRA E COMERCIAL", fSecao));
            document.add(new Paragraph("Faturamento Bruto: R$ " + fatBruto.setScale(2, RoundingMode.HALF_UP) +
                    " | Lucro Bruto: R$ " + lucroBruto.setScale(2, RoundingMode.HALF_UP) +
                    " | Ticket Médio: R$ " + ticketMedio.setScale(2, RoundingMode.HALF_UP), fNormal));

            String iaFinanceiro = gerarConselhoFinanceiro(fatBruto, lucroBruto, ticketMedio);
            Paragraph pIaFin = new Paragraph("🤖 Parecer da Consultora IA: " + iaFinanceiro, fIA);
            pIaFin.setSpacingBefore(10); pIaFin.setSpacingAfter(20);
            document.add(pIaFin);

            document.add(new Paragraph("2. SAÚDE DO ESTOQUE", fSecao));
            document.add(new Paragraph("Taxa de Ruptura (Faltas): " + ruptura + "% do Mix de Produtos.", fNormal));

            String iaEstoque = gerarConselhoEstoque(ruptura);
            Paragraph pIaEst = new Paragraph("🤖 Parecer da Consultora IA: " + iaEstoque, fIA);
            pIaEst.setSpacingBefore(10); pIaEst.setSpacingAfter(20);
            document.add(pIaEst);

            document.add(new Paragraph("3. RISCO FISCAL E TRIBUTÁRIO", fSecao));
            document.add(new Paragraph("Produtos sem NCM/CEST cadastrados: " + errosFiscais, fNormal));

            String iaFiscal = gerarConselhoFiscal(errosFiscais);
            Paragraph pIaFis = new Paragraph("🤖 Parecer da Consultora IA: " + iaFiscal, fIA);
            pIaFis.setSpacingBefore(10); pIaFis.setSpacingAfter(30);
            document.add(pIaFis);

            PdfPTable tabela = new PdfPTable(2);
            tabela.setWidthPercentage(100);

            PdfPCell cellHeader = new PdfPCell(new Phrase("Métricas Chave do Período", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE)));
            cellHeader.setColspan(2); cellHeader.setBackgroundColor(new Color(15, 23, 42)); cellHeader.setPadding(8);
            tabela.addCell(cellHeader);

            tabela.addCell(criarCelula("Total de Vendas Realizadas", fNormal));
            tabela.addCell(criarCelulaDireita(String.valueOf(vendas.getQuantidadeVendas()), fNormal));
            tabela.addCell(criarCelula("Margem de Lucro Bruta", fNormal));

            BigDecimal margem = fatBruto.compareTo(BigDecimal.ZERO) > 0 ? lucroBruto.divide(fatBruto, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;
            tabela.addCell(criarCelulaDireita(margem.setScale(1, RoundingMode.HALF_UP) + "%", fNormal));

            document.add(tabela);

            document.add(new Paragraph("\n\nDocumento confidencial gerado automaticamente pelo Sistema DD Cosméticos.", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.LIGHT_GRAY)));
            document.close();

            String nomeArquivoPdf = "Consultoria_DDCosmeticos_" + mesReferencia.replace("/", "_") + ".pdf";
            emailService.enviarRelatorioAdmin(out.toByteArray(), nomeArquivoPdf, emailAdmin);

        } catch (Exception e) {
            log.error("❌ Falha na geração do Relatório Mensal Automático: ", e);
            throw new RuntimeException("Falha na geração: " + e.getMessage());
        }
    }

    // =========================================================================
    // MOTORES DE INTELIGÊNCIA (HEURÍSTICA DE CONSULTORIA)
    // =========================================================================

    private String gerarConselhoFinanceiro(BigDecimal fatBruto, BigDecimal lucroBruto, BigDecimal ticketMedio) {
        if (fatBruto.compareTo(BigDecimal.ZERO) == 0) return "Não há dados de faturamento suficientes neste período para uma análise profunda.";

        BigDecimal margem = lucroBruto.divide(fatBruto, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        StringBuilder conselho = new StringBuilder();

        if (margem.compareTo(new BigDecimal("35")) > 0) {
            conselho.append("Excelente trabalho! A sua margem de lucro operacional (").append(margem.setScale(1, RoundingMode.HALF_UP)).append("%) está acima da média do retalho de cosméticos. ");
        } else if (margem.compareTo(new BigDecimal("20")) < 0) {
            conselho.append("Atenção: A sua margem de lucro está espremida (").append(margem.setScale(1, RoundingMode.HALF_UP)).append("%). Sugiro rever a precificação dos produtos Curva A ou negociar custos menores com fornecedores. ");
        } else {
            conselho.append("A margem de lucro está saudável e dentro do padrão do mercado. ");
        }

        if (ticketMedio.compareTo(new BigDecimal("80")) < 0) {
            conselho.append("No entanto, o Ticket Médio (R$ ").append(ticketMedio.setScale(2, RoundingMode.HALF_UP)).append(") está baixo. Treine a sua equipa para oferecer 'Cross-Sell' (ex: oferecer condicionador sempre que venderem um champô).");
        } else {
            conselho.append("O Ticket Médio está num patamar forte, demonstrando boa capacidade de venda agregada da equipa.");
        }
        return conselho.toString();
    }

    private String gerarConselhoEstoque(double ruptura) {
        if (ruptura > 5.0) {
            return "ALERTA CRÍTICO: " + ruptura + "% do seu catálogo ativo está com estoque zerado. Você está a perder vendas diariamente porque os clientes entram na loja e não encontram o que procuram. Acione o setor de compras imediatamente com foco nos produtos de Curva A.";
        } else if (ruptura > 0) {
            return "Ruptura sob controle (" + ruptura + "%), mas requer monitoramento semanal. Garanta que as marcas de alto giro (ex: Haskell, Lola) não cheguem ao estoque de segurança mínimo.";
        } else {
            return "Parabéns! A sua gestão de prateleira está perfeita (0% de ruptura). O cliente encontra sempre o que precisa.";
        }
    }

    private String gerarConselhoFiscal(Long errosFiscais) {
        if (errosFiscais > 10) {
            return "ALTO RISCO DE AUTUAÇÃO: Você tem " + errosFiscais + " produtos sem NCM ou CEST configurados. Isto bloqueia a emissão de NFC-e e sujeita a loja a multas severas da SEFAZ de Pernambuco. Corrija o cadastro urgente na aba de Produtos.";
        } else if (errosFiscais > 0) {
            return "Risco Moderado: Existem " + errosFiscais + " produtos com pendências fiscais (NCM/CEST). Revise-os para evitar rejeição de notas fiscais no caixa.";
        } else {
            return "Compliance perfeito! 100% do seu catálogo está validado fiscalmente. Pode operar com tranquilidade.";
        }
    }

    private PdfPCell criarCelula(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        cell.setPadding(8);
        return cell;
    }

    private PdfPCell criarCelulaDireita(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }
}