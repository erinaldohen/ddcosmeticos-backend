package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioMensalService {

    private final VendaRepository vendaRepository;
    private final CaixaDiarioRepository caixaRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remetente;

    /**
     * Gera o PDF e envia por e-mail de forma assíncrona.
     */
    @Async
    public void processarRelatorioMensal(String emailDestino) {
        try {
            LocalDate mesAnterior = LocalDate.now().minusMonths(1);
            LocalDateTime inicio = mesAnterior.withDayOfMonth(1).atStartOfDay();
            LocalDateTime fim = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth()).atTime(LocalTime.MAX);

            // 1. Busca Dados (Usando java.util.List explicitamente para evitar conflito com iText)
            BigDecimal faturamento = vendaRepository.somarFaturamentoTotal(inicio, fim);
            java.util.List<VendaPorPagamentoDTO> porPagamento = vendaRepository.agruparPorFormaPagamento(inicio, fim);
            BigDecimal quebras = caixaRepository.somarQuebrasDeCaixa(inicio, fim);

            if (faturamento == null) faturamento = BigDecimal.ZERO;
            if (quebras == null) quebras = BigDecimal.ZERO;

            // 2. Gera PDF
            byte[] pdfBytes = gerarPdf(mesAnterior, faturamento, quebras, porPagamento);

            // 3. Envia E-mail
            enviarEmail(emailDestino, mesAnterior, pdfBytes);

            log.info("Relatório mensal enviado para {}", emailDestino);

        } catch (Exception e) {
            log.error("Erro ao gerar relatório mensal", e);
        }
    }

    private byte[] gerarPdf(LocalDate dataRef, BigDecimal faturamento, BigDecimal quebras, java.util.List<VendaPorPagamentoDTO> pagamentos) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Fontes
            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
            Font textoFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK);
            Font subTituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.WHITE);

            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

            // Cabeçalho
            Paragraph titulo = new Paragraph("Relatório Gerencial DDCosméticos", tituloFont);
            titulo.setAlignment(Element.ALIGN_CENTER);
            document.add(titulo);

            document.add(new Paragraph("Referência: " + dataRef.format(DateTimeFormatter.ofPattern("MMMM/yyyy", new Locale("pt", "BR"))).toUpperCase(), textoFont));
            document.add(new Paragraph("Gerado em: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), textoFont));
            document.add(Chunk.NEWLINE);

            // === Tabela de Resumo ===
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);

            adicionarLinhaTabela(table, "Faturamento Total", nf.format(faturamento), new Color(220, 255, 220));

            // Estimativa de Lucro (Exemplo: 40%)
            adicionarLinhaTabela(table, "Lucro Bruto Estimado (40%)", nf.format(faturamento.multiply(new BigDecimal("0.4"))), new Color(240, 240, 255));

            // Quebras
            Color corQuebra = quebras.compareTo(BigDecimal.ZERO) < 0 ? new Color(255, 220, 220) : Color.WHITE;
            adicionarLinhaTabela(table, "Quebras de Caixa", nf.format(quebras), corQuebra);

            document.add(table);
            document.add(Chunk.NEWLINE);

            // === Detalhamento por Pagamento ===
            document.add(new Paragraph("Detalhamento por Método:", subTituloFont));

            // Alterado para 3 colunas: Método | Qtd | Valor
            PdfPTable tablePag = new PdfPTable(new float[]{3, 1, 2});
            tablePag.setWidthPercentage(100);
            tablePag.setSpacingBefore(5f);

            // Cabeçalho da Tabela
            adicionarCelulaHeader(tablePag, "Método", headerFont);
            adicionarCelulaHeader(tablePag, "Qtd", headerFont);
            adicionarCelulaHeader(tablePag, "Valor Total", headerFont);

            for (VendaPorPagamentoDTO dto : pagamentos) {
                // Acesso via Record (.formaPagamento(), .quantidade(), .valorTotal())
                String forma = dto.formaPagamento() != null ? dto.formaPagamento().name() : "N/A";
                String qtd = dto.quantidade() != null ? dto.quantidade().toString() : "0";
                String valor = nf.format(dto.valorTotal());

                adicionarCelula(tablePag, forma, Element.ALIGN_LEFT);
                adicionarCelula(tablePag, qtd, Element.ALIGN_CENTER);
                adicionarCelula(tablePag, valor, Element.ALIGN_RIGHT);
            }
            document.add(tablePag);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criar PDF", e);
        }
        return out.toByteArray();
    }

    // Auxiliar para Tabela de Resumo (2 colunas)
    private void adicionarLinhaTabela(PdfPTable table, String label, String valor, Color bg) {
        PdfPCell c1 = new PdfPCell(new Phrase(label));
        c1.setBackgroundColor(bg);
        c1.setPadding(8);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(valor));
        c2.setBackgroundColor(bg);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setPadding(8);
        table.addCell(c2);
    }

    // Auxiliar para Cabeçalho da Tabela de Pagamentos
    private void adicionarCelulaHeader(PdfPTable table, String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(Color.GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        table.addCell(cell);
    }

    // Auxiliar para Linhas da Tabela de Pagamentos
    private void adicionarCelula(PdfPTable table, String texto, int alinhamento) {
        PdfPCell cell = new PdfPCell(new Phrase(texto));
        cell.setHorizontalAlignment(alinhamento);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void enviarEmail(String destinatario, LocalDate dataRef, byte[] anexo) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(remetente);
        helper.setTo(destinatario);
        helper.setSubject("Relatório Mensal - " + dataRef.format(DateTimeFormatter.ofPattern("MM/yyyy")));

        String corpo = String.format("""
            Olá,
            
            O fechamento do mês de %s foi processado.
            Segue em anexo o relatório detalhado de faturamento e quebras.
            
            Sistema DDCosméticos
            """, dataRef.format(DateTimeFormatter.ofPattern("MMMM/yyyy", new Locale("pt", "BR"))));

        helper.setText(corpo);
        helper.addAttachment("Resumo_" + dataRef.toString() + ".pdf", new ByteArrayResource(anexo));

        mailSender.send(message);
    }
}