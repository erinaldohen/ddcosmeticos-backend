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
     * Gera o PDF e envia por e-mail de forma assíncrona (em background).
     */
    @Async
    public void processarRelatorioMensal(String emailDestino) {
        try {
            LocalDate mesAnterior = LocalDate.now().minusMonths(1);
            LocalDateTime inicio = mesAnterior.withDayOfMonth(1).atStartOfDay();
            LocalDateTime fim = mesAnterior.withDayOfMonth(mesAnterior.lengthOfMonth()).atTime(LocalTime.MAX);

            // 1. Busca Dados
            BigDecimal faturamento = vendaRepository.somarFaturamento(inicio, fim);
            java.util.List<VendaPorPagamentoDTO> porPagamento = vendaRepository.agruparPorFormaPagamento(inicio, fim);
            BigDecimal quebras = caixaRepository.somarQuebrasDeCaixa(inicio, fim);

            // Null Safety
            faturamento = faturamento != null ? faturamento : BigDecimal.ZERO;
            quebras = quebras != null ? quebras : BigDecimal.ZERO;

            // 2. Gera PDF
            byte[] pdfBytes = gerarPdf(mesAnterior, faturamento, quebras, porPagamento);

            // 3. Envia E-mail
            enviarEmail(emailDestino, mesAnterior, pdfBytes);

            log.info("Relatório mensal gerado e enviado com sucesso para {}", emailDestino);

        } catch (Exception e) {
            log.error("Erro crítico ao gerar/enviar o relatório mensal para o email: {}", emailDestino, e);
        }
    }

    private byte[] gerarPdf(LocalDate dataRef, BigDecimal faturamento, BigDecimal quebras, java.util.List<VendaPorPagamentoDTO> pagamentos) {
        // Uso do try-with-resources garante que a memória do stream será liberada
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            try {
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
                adicionarLinhaTabela(table, "Lucro Bruto Estimado (40%)", nf.format(faturamento.multiply(new BigDecimal("0.4"))), new Color(240, 240, 255));

                Color corQuebra = quebras.compareTo(BigDecimal.ZERO) < 0 ? new Color(255, 220, 220) : Color.WHITE;
                adicionarLinhaTabela(table, "Quebras de Caixa (Diferença)", nf.format(quebras), corQuebra);

                document.add(table);
                document.add(Chunk.NEWLINE);

                // === Detalhamento por Pagamento ===
                document.add(new Paragraph("Detalhamento por Método de Pagamento:", subTituloFont));

                PdfPTable tablePag = new PdfPTable(new float[]{3, 1, 2});
                tablePag.setWidthPercentage(100);
                tablePag.setSpacingBefore(10f);

                adicionarCelulaHeader(tablePag, "Método", headerFont);
                adicionarCelulaHeader(tablePag, "Qtd. Transações", headerFont);
                adicionarCelulaHeader(tablePag, "Valor Total", headerFont);

                if (pagamentos != null) {
                    for (VendaPorPagamentoDTO dto : pagamentos) {
                        // Null Safety para evitar quebra do PDF se a query retornar algo nulo
                        String forma = dto.formaPagamento() != null ? dto.formaPagamento().name() : "N/A";
                        String qtd = dto.quantidade() != null ? dto.quantidade().toString() : "0";
                        BigDecimal valorTotal = dto.valorTotal() != null ? dto.valorTotal() : BigDecimal.ZERO;

                        adicionarCelula(tablePag, forma, Element.ALIGN_LEFT);
                        adicionarCelula(tablePag, qtd, Element.ALIGN_CENTER);
                        adicionarCelula(tablePag, nf.format(valorTotal), Element.ALIGN_RIGHT);
                    }
                }
                document.add(tablePag);

            } finally {
                // Garante que o documento será fechado mesmo se der erro na formatação
                if (document.isOpen()) {
                    document.close();
                }
            }
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Falha na geração do PDF", e);
        }
    }

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

    private void adicionarCelulaHeader(PdfPTable table, String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(Color.DARK_GRAY); // Escurecido para melhor leitura
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(8); // Aumentado para respirar
        table.addCell(cell);
    }

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
        helper.setSubject("Relatório Mensal Financeiro - " + dataRef.format(DateTimeFormatter.ofPattern("MM/yyyy")));

        String corpo = String.format("""
            Olá Administrador,
            
            O fechamento do mês de %s foi processado com sucesso.
            Segue em anexo o relatório gerencial contendo o faturamento, lucro bruto e auditoria de caixas (quebras).
            
            Atenciosamente,
            Sistema Automatizado DDCosméticos
            """, dataRef.format(DateTimeFormatter.ofPattern("MMMM/yyyy", new Locale("pt", "BR"))));

        helper.setText(corpo);
        helper.addAttachment("DDCosmeticos_Fechamento_" + dataRef.toString() + ".pdf", new ByteArrayResource(anexo));

        mailSender.send(message);
    }
}