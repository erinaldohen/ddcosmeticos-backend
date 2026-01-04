package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import com.lowagie.text.*;
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
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class RelatorioService {

    @Autowired
    private VendaRepository vendaRepository;

    @Transactional(readOnly = true)
    public RelatorioVendasDTO gerarRelatorioVendas(LocalDate inicio, LocalDate fim) {
        // 1. Definição do Período
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();

        // 2. Execução das Consultas Otimizadas (JPQL)
        BigDecimal faturamentoTotal = vendaRepository.somarFaturamentoNoPeriodo(dataInicio, dataFim);
        if (faturamentoTotal == null) faturamentoTotal = BigDecimal.ZERO;

        List<VendaDiariaDTO> evolucaoDiaria = vendaRepository.agruparVendasPorDia(dataInicio, dataFim);
        List<VendaPorPagamentoDTO> vendasPorPagamento = vendaRepository.agruparPorFormaPagamento(dataInicio, dataFim);

        // Ranking Top 10
        List<ProdutoRankingDTO> rankingProdutos = vendaRepository.buscarRankingProdutos(dataInicio, dataFim, PageRequest.of(0, 10));

        // 3. Cálculos Complementares (Ticket Médio)
        // CORREÇÃO: Usando o getter correto 'getQuantidade()' baseado no DTO
        long totalVendas = vendasPorPagamento.stream().mapToLong(VendaPorPagamentoDTO::getQuantidadeVendas).sum();

        BigDecimal ticketMedio = (totalVendas > 0)
                ? faturamentoTotal.divide(BigDecimal.valueOf(totalVendas), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String periodoStr = dataInicio.format(fmt) + " a " + dataFim.format(fmt);

        // 4. Montagem do DTO Final
        return RelatorioVendasDTO.builder()
                .dataGeracao(LocalDateTime.now())
                .periodo(periodoStr)
                .faturamentoTotal(faturamentoTotal)
                .totalVendasRealizadas(totalVendas)
                .ticketMedio(ticketMedio)
                .evolucaoDiaria(evolucaoDiaria)
                .vendasPorPagamento(vendasPorPagamento)
                .produtosMaisVendidos(rankingProdutos)
                .build();
    }

    // ==================================================================================
    // SESSÃO 2: GERAÇÃO DE PDF E ETIQUETAS
    // ==================================================================================

    public byte[] gerarPdfSugestaoCompras(List<SugestaoCompraDTO> sugestoes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            // Cabeçalho
            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("RELATÓRIO DE REPOSIÇÃO INTELIGENTE", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);
            document.add(new Paragraph("Gerado em: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())));
            document.add(new Paragraph(" "));

            // Tabela
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 2f, 2f, 1f, 1f, 1.5f, 2f});

            String[] headers = {"Produto", "Marca", "Urgência", "Atual", "Mín", "Comprar", "Investimento"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
                cell.setBackgroundColor(Color.DARK_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(6);
                table.addCell(cell);
            }

            NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

            for (SugestaoCompraDTO item : sugestoes) {
                table.addCell(criarCelula(item.getNomeProduto(), Element.ALIGN_LEFT));
                table.addCell(criarCelula(item.getMarca(), Element.ALIGN_LEFT));

                PdfPCell cellUrgencia = new PdfPCell(new Phrase(item.getNivelUrgencia(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                cellUrgencia.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellUrgencia.setPadding(4);
                if (item.getNivelUrgencia().contains("CRÍTICO")) cellUrgencia.setBackgroundColor(new Color(255, 200, 200));
                else if (item.getNivelUrgencia().contains("ALERTA")) cellUrgencia.setBackgroundColor(new Color(255, 255, 200));
                else cellUrgencia.setBackgroundColor(new Color(220, 255, 220));
                table.addCell(cellUrgencia);

                table.addCell(criarCelula(String.valueOf(item.getEstoqueAtual()), Element.ALIGN_CENTER));
                table.addCell(criarCelula(String.valueOf(item.getEstoqueMinimoCalculado()), Element.ALIGN_CENTER));

                PdfPCell cellSugerido = new PdfPCell(new Phrase(String.valueOf(item.getQuantidadeSugeridaCompra()), FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
                cellSugerido.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellSugerido.setPadding(4);
                table.addCell(cellSugerido);

                table.addCell(criarCelula(moeda.format(item.getCustoEstimadoPedido()), Element.ALIGN_RIGHT));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erro ao gerar PDF", e);
            throw new RuntimeException("Erro PDF", e);
        }
    }

    private PdfPCell criarCelula(String texto, int alinhamento) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setHorizontalAlignment(alinhamento);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        return cell;
    }

    public String gerarEtiquetaTermica(Produto p) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================\n");
        sb.append("      DD COSMETICOS\n");
        sb.append("================================\n\n");
        String nome = p.getDescricao().length() > 32 ? p.getDescricao().substring(0, 32) : p.getDescricao();
        sb.append(nome).append("\n\n");
        sb.append("R$ ").append(String.format("%.2f", p.getPrecoVenda())).append("\n\n");
        sb.append("COD: ").append(p.getCodigoBarras()).append("\n");
        sb.append("\n\n\n");
        return sb.toString();
    }
}