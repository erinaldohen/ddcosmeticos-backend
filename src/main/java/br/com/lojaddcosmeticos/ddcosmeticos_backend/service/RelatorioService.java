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

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class RelatorioService {

    @Autowired
    private VendaRepository vendaRepository;

    public RelatorioVendasDTO gerarRelatorioVendas(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(LocalTime.MAX);

        // 1. Busca dados reais das queries consolidadas
        BigDecimal totalFaturado = vendaRepository.somarFaturamentoNoPeriodo(dataInicio, dataFim);
        List<VendaDiariaDTO> vendasDiarias = vendaRepository.agruparVendasPorDia(dataInicio, dataFim);
        List<VendaPorPagamentoDTO> porPagamento = vendaRepository.agruparPorFormaPagamento(dataInicio, dataFim);

        // Busca Top Marcas para o gráfico (Top 5)
        List<ProdutoRankingDTO> rankingMarcas = vendaRepository.buscarRankingMarcas(dataInicio, dataFim, PageRequest.of(0, 5));

        // 2. Cálculos para os Cards do Dashboard
        long totalVendasCount = vendasDiarias.stream()
                .mapToLong(v -> v.getQuantidade() != null ? v.getQuantidade() : 0L)
                .sum();

        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (totalVendasCount > 0) {
            ticketMedio = totalFaturado.divide(new BigDecimal(totalVendasCount), 2, RoundingMode.HALF_UP);
        }

        BigDecimal lucroEstimado = totalFaturado.multiply(new BigDecimal("0.35")); // Baseado na margem padrão da loja

        // 3. Retorno mapeado para o RelatorioVendasDTO (alinhado com o React)
        return RelatorioVendasDTO.builder()
                .dataGeracao(LocalDateTime.now())
                .totalFaturado(totalFaturado)
                .quantidadeVendas((int) totalVendasCount)
                .ticketMedio(ticketMedio)
                .lucroBrutoEstimado(lucroEstimado)
                .vendasDiarias(vendasDiarias)     // Popula gráfico "Tendência Diária"
                .porPagamento(porPagamento)       // Popula gráfico "Meios de Pagamento"
                .rankingMarcas(rankingMarcas)     // Popula gráfico "Top Marcas"
                .build();
    }

    // --- MÉTODOS DE PDF E ETIQUETAS (MANTIDOS) ---

    public byte[] gerarPdfSugestaoCompras(List<SugestaoCompraDTO> sugestoes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph titulo = new Paragraph("RELATÓRIO DE REPOSIÇÃO INTELIGENTE", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);
            document.add(new Paragraph("Gerado em: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 2f, 2f, 1f, 1f, 1.5f, 2f});

            String[] headers = {"Produto", "Marca", "Urgência", "Atual", "Mín", "Comprar", "Investimento"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
                cell.setBackgroundColor(Color.DARK_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(6);
                table.addCell(cell);
            }

            NumberFormat moeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
            for (SugestaoCompraDTO item : sugestoes) {
                table.addCell(criarCelula(item.getDescricao(), Element.ALIGN_LEFT));
                table.addCell(criarCelula(item.getMarca(), Element.ALIGN_LEFT));

                PdfPCell cellUrgencia = new PdfPCell(new Phrase(item.getNivelUrgencia(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                cellUrgencia.setHorizontalAlignment(Element.ALIGN_CENTER);
                if (item.getNivelUrgencia().contains("CRÍTICO")) cellUrgencia.setBackgroundColor(new Color(255, 200, 200));
                else if (item.getNivelUrgencia().contains("ALERTA")) cellUrgencia.setBackgroundColor(new Color(255, 255, 200));
                else cellUrgencia.setBackgroundColor(new Color(220, 255, 220));
                table.addCell(cellUrgencia);

                table.addCell(criarCelula(String.valueOf(item.getEstoqueAtual()), Element.ALIGN_CENTER));
                table.addCell(criarCelula(String.valueOf(item.getEstoqueMinimo()), Element.ALIGN_CENTER));
                table.addCell(criarCelula(String.valueOf(item.getQuantidadeSugerida()), Element.ALIGN_CENTER));
                table.addCell(criarCelula(moeda.format(item.getCustoEstimado()), Element.ALIGN_RIGHT));
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF", e);
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
        sb.append("COD: ").append(p.getCodigoBarras()).append("\n\n\n\n");
        return sb.toString();
    }
}