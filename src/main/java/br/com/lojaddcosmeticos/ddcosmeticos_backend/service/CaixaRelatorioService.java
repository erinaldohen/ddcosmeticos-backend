package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class CaixaRelatorioService {

    public void exportarPdf(HttpServletResponse response, List<CaixaDiario> listaCaixas, String inicio, String fim) throws IOException {
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, response.getOutputStream());

        document.open();

        // 1. Título e Cabeçalho
        Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        fontTitulo.setSize(18);
        fontTitulo.setColor(new Color(99, 102, 241)); // Roxo da marca

        Paragraph p = new Paragraph("Relatório de Histórico de Caixa", fontTitulo);
        p.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(p);

        Font fontSub = FontFactory.getFont(FontFactory.HELVETICA);
        fontSub.setSize(12);
        Paragraph p2 = new Paragraph("Período: " + inicio + " a " + fim, fontSub);
        p2.setAlignment(Paragraph.ALIGN_CENTER);
        p2.setSpacingAfter(20);
        document.add(p2);

        // 2. Tabela
        PdfPTable table = new PdfPTable(6); // 6 Colunas
        table.setWidthPercentage(100f);
        table.setWidths(new float[] {1.5f, 3.0f, 3.0f, 2.5f, 2.5f, 2.0f});
        table.setSpacingBefore(10);

        // Cabeçalho da Tabela
        writeTableHeader(table);

        // Dados
        writeDataRows(table, listaCaixas);

        document.add(table);

        // 3. Rodapé com Totais
        document.add(new Paragraph("\n"));
        double total = listaCaixas.stream()
                .filter(c -> c.getStatus().name().equals("FECHADO"))
                .mapToDouble(c -> c.getValorFechamento() != null ? c.getValorFechamento().doubleValue() : 0.0)
                .sum();

        Font fontTotal = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        Paragraph pTotal = new Paragraph("Total Movimentado (Fechados): " + formatMoney(total), fontTotal);
        pTotal.setAlignment(Paragraph.ALIGN_RIGHT);
        document.add(pTotal);

        document.close();
    }

    private void writeTableHeader(PdfPTable table) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(248, 250, 252));
        cell.setPadding(8);

        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        font.setSize(10);
        font.setColor(new Color(15, 23, 42));

        String[] headers = {"ID", "Data Abertura", "Operador", "Saldo Inicial", "Valor Final", "Status"};

        for (String header : headers) {
            cell.setPhrase(new Phrase(header, font));
            table.addCell(cell);
        }
    }

    private void writeDataRows(PdfPTable table, List<CaixaDiario> lista) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");
        Font font = FontFactory.getFont(FontFactory.HELVETICA);
        font.setSize(9);

        for (CaixaDiario c : lista) {
            table.addCell(new Phrase(String.valueOf(c.getId()), font));
            table.addCell(new Phrase(c.getDataAbertura().format(dtf), font));

            // Tratamento seguro do operador
            String operador = "Sistema";
            if (c.getUsuarioAbertura() != null) {
                operador = c.getUsuarioAbertura().getNome(); // Supondo que seja o objeto Usuario
            }
            table.addCell(new Phrase(operador, font));

            table.addCell(new Phrase(formatMoney(c.getSaldoInicial().doubleValue()), font));

            Double vFinal = c.getValorFechamento() != null ? c.getValorFechamento().doubleValue() : 0.0;
            table.addCell(new Phrase(formatMoney(vFinal), font));

            table.addCell(new Phrase(c.getStatus().toString(), font));
        }
    }

    private String formatMoney(Double val) {
        return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(val);
    }
}