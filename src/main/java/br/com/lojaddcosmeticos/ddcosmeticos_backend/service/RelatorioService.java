package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class RelatorioService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private AuditoriaRepository auditoriaRepository; // Injeção necessária para o PDF de auditoria

    public RelatorioVendasDTO gerarRelatorioVendas(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(LocalTime.MAX);

        BigDecimal totalFaturado = vendaRepository.somarFaturamento(dataInicio, dataFim);
        if (totalFaturado == null) totalFaturado = BigDecimal.ZERO;

        List<VendaDiariaDTO> vendasDiarias = vendaRepository.agruparVendasPorDia(dataInicio, dataFim);
        List<VendaPorPagamentoDTO> porPagamento = vendaRepository.agruparPorFormaPagamento(dataInicio, dataFim);
        List<ProdutoRankingDTO> rankingMarcas = vendaRepository.buscarRankingMarcas(dataInicio, dataFim, PageRequest.of(0, 5));

        long totalVendasCount = vendasDiarias.stream()
                .mapToLong(v -> v.getQuantidade() != null ? v.getQuantidade() : 0L)
                .sum();

        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (totalVendasCount > 0) {
            ticketMedio = totalFaturado.divide(new BigDecimal(totalVendasCount), 2, RoundingMode.HALF_UP);
        }

        BigDecimal lucroEstimado = totalFaturado.multiply(new BigDecimal("0.35"));

        return RelatorioVendasDTO.builder()
                .dataGeracao(LocalDateTime.now())
                .totalFaturado(totalFaturado)
                .quantidadeVendas((int) totalVendasCount)
                .ticketMedio(ticketMedio)
                .lucroBrutoEstimado(lucroEstimado)
                .vendasDiarias(vendasDiarias)
                .porPagamento(porPagamento)
                .rankingMarcas(rankingMarcas)
                .build();
    }

    // --- MÉTODOS DE PDF ---

    /**
     * Método novo para atender a chamada do Controller na linha 46.
     * Gera PDF de auditoria filtrado.
     */
    public byte[] gerarPdfAuditoria(String search, String inicioStr, String fimStr) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            // Cabeçalho
            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Paragraph titulo = new Paragraph("RELATÓRIO CONSOLIDADO DE AUDITORIA", fontTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            Font fontSub = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
            Paragraph sub = new Paragraph("Cópia Controlada - DD Cosméticos - Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), fontSub);
            sub.setAlignment(Element.ALIGN_CENTER);
            document.add(sub);
            document.add(new Paragraph(" ")); // Espaço

            // Filtros de Data
            LocalDateTime dataInicio = (inicioStr != null && !inicioStr.isEmpty())
                    ? LocalDate.parse(inicioStr).atStartOfDay()
                    : LocalDateTime.of(1970, 1, 1, 0, 0);

            LocalDateTime dataFim = (fimStr != null && !fimStr.isEmpty())
                    ? LocalDate.parse(fimStr).atTime(LocalTime.MAX)
                    : LocalDateTime.now().plusDays(1);

            String termo = (search != null) ? search.toLowerCase() : "";

            // Busca os dados (Assumindo que o repositório tem o método buscarListaPorFiltros)
            // Se não tiver, use findAll e filtre via stream Java, ou crie a query no repo.
            // Aqui usaremos findAll com filtro Java por segurança se o repo não tiver a query pronta
            List<Auditoria> logs = auditoriaRepository.findAllByOrderByDataHoraDesc().stream()
                    .filter(a -> a.getDataHora().isAfter(dataInicio) && a.getDataHora().isBefore(dataFim))
                    .filter(a -> termo.isEmpty()
                            || (a.getUsuarioResponsavel() != null && a.getUsuarioResponsavel().toLowerCase().contains(termo))
                            || (a.getMensagem() != null && a.getMensagem().toLowerCase().contains(termo))
                            || (a.getTipoEvento() != null && a.getTipoEvento().toLowerCase().contains(termo)))
                    .limit(500) // Limite de segurança
                    .toList();

            // Tabela
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.5f, 2f, 2.5f, 4f}); // Data, User, Evento, Msg

            String[] headers = {"Data/Hora", "Usuário", "Evento", "Descrição"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
                cell.setBackgroundColor(new Color(30, 41, 59)); // Azul escuro
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(6);
                table.addCell(cell);
            }

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
            boolean zebra = false;

            for (Auditoria log : logs) {
                Color bg = zebra ? new Color(241, 245, 249) : Color.WHITE;

                table.addCell(criarCelulaComBg(log.getDataHora().format(dtf), Element.ALIGN_CENTER, bg));
                table.addCell(criarCelulaComBg(log.getUsuarioResponsavel(), Element.ALIGN_CENTER, bg));
                table.addCell(criarCelulaComBg(log.getTipoEvento(), Element.ALIGN_CENTER, bg));
                table.addCell(criarCelulaComBg(log.getMensagem(), Element.ALIGN_LEFT, bg));

                zebra = !zebra;
            }

            document.add(table);
            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Erro ao gerar PDF de Auditoria", e);
            throw new RuntimeException("Erro na geração do relatório.", e);
        }
    }

    // Método auxiliar para célula com cor de fundo (Zebra Striping)
    private PdfPCell criarCelulaComBg(String texto, int alinhamento, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setHorizontalAlignment(alinhamento);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBackgroundColor(bg);
        cell.setPadding(4);
        return cell;
    }

    public byte[] gerarPdfSugestaoCompras(List<SugestaoCompraDTO> sugestoes) {
        // ... (código existente mantido) ...
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
                table.addCell(criarCelula(item.descricao(), Element.ALIGN_LEFT));
                table.addCell(criarCelula(item.marca(), Element.ALIGN_LEFT));

                PdfPCell cellUrgencia = new PdfPCell(new Phrase(item.nivelUrgencia(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                cellUrgencia.setHorizontalAlignment(Element.ALIGN_CENTER);
                if (item.nivelUrgencia().contains("CRÍTICO")) cellUrgencia.setBackgroundColor(new Color(255, 200, 200));
                else if (item.nivelUrgencia().contains("ALERTA")) cellUrgencia.setBackgroundColor(new Color(255, 255, 200));
                else cellUrgencia.setBackgroundColor(new Color(220, 255, 220));
                table.addCell(cellUrgencia);

                table.addCell(criarCelula(String.valueOf(item.estoqueAtual()), Element.ALIGN_CENTER));
                table.addCell(criarCelula(String.valueOf(item.estoqueMinimo()), Element.ALIGN_CENTER));
                table.addCell(criarCelula(String.valueOf(item.quantidadeSugerida()), Element.ALIGN_CENTER));
                table.addCell(criarCelula(moeda.format(item.custoEstimado()), Element.ALIGN_RIGHT));
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