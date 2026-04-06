package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioMensalService {

    private final ConfiguracaoLojaService configuracaoLojaService;

    // 🚨 1. Usaremos o EntityManager para rodar as consultas direto aqui, sem depender de Repositories!
    private final EntityManager entityManager;

    // 🚨 2. Chamadas de SMTP corrigidas para buscar na raiz de config
    private JavaMailSenderImpl criarCarteiroDinamico() {
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // Lendo diretamente da raiz (removido o getFiscal())
        mailSender.setHost(config.getSmtpHost() != null ? config.getSmtpHost() : "smtp.gmail.com");
        mailSender.setPort(config.getSmtpPort() != null ? config.getSmtpPort() : 587);
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");

        return mailSender;
    }

    public void gerarEEnviarRelatorioMensal(String emailDestino) throws Exception {
        // Define o período (Mês anterior completo)
        LocalDateTime inicio = LocalDate.now().minusMonths(1).withDayOfMonth(1).atStartOfDay();
        LocalDateTime fim = LocalDate.now().minusMonths(1).withDayOfMonth(LocalDate.now().minusMonths(1).lengthOfMonth()).atTime(23, 59, 59);

        // =========================================================================
        // 1. COLETA DE DADOS (Consultas seguras e diretas via EntityManager)
        // =========================================================================
        BigDecimal faturamentoTotal = (BigDecimal) entityManager.createQuery(
                        "SELECT COALESCE(SUM(v.valorTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> 'CANCELADA'")
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getSingleResult();

        BigDecimal custoTotal = (BigDecimal) entityManager.createQuery(
                        "SELECT COALESCE(SUM(v.custoTotal), 0) FROM Venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> 'CANCELADA'")
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getSingleResult();

        BigDecimal lucroBruto = faturamentoTotal.subtract(custoTotal);

        BigDecimal quebrasDeCaixa = (BigDecimal) entityManager.createQuery(
                        "SELECT COALESCE(SUM(c.diferencaCaixa), 0) FROM CaixaDiario c WHERE c.dataAbertura BETWEEN :inicio AND :fim")
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getSingleResult();

        // Lista com as Formas de Pagamento e os Totais Vendidos
        List<Object[]> vendasPorMetodo = entityManager.createQuery(
                        "SELECT p.formaPagamento, SUM(p.valor) FROM PagamentoVenda p JOIN p.venda v WHERE v.dataVenda BETWEEN :inicio AND :fim AND v.statusNfce <> 'CANCELADA' GROUP BY p.formaPagamento", Object[].class)
                .setParameter("inicio", inicio)
                .setParameter("fim", fim)
                .getResultList();

        // =========================================================================
        // 2. GERAÇÃO DO PDF
        // =========================================================================
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);

        document.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Paragraph title = new Paragraph("Resumo Executivo Mensal - DD Cosméticos", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph("Período: " + inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) +
                " a " + fim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell("Faturamento Total"); table.addCell("R$ " + faturamentoTotal);
        table.addCell("Lucro Bruto (Vendas - Custo)"); table.addCell("R$ " + lucroBruto);
        table.addCell("Quebras de Caixa (Balanço)"); table.addCell("R$ " + quebrasDeCaixa);
        document.add(table);

        document.add(new Paragraph("\nResumo por Método de Pagamento:"));
        for (Object[] row : vendasPorMetodo) {
            try {
                String metodo = row[0] != null ? row[0].toString() : "DESCONHECIDO";
                BigDecimal valor = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                document.add(new Paragraph("- " + metodo + ": R$ " + valor));
            } catch (DocumentException e) {
                log.error("Erro ao inserir metodo no PDF", e);
            }
        }

        document.close();

        // =========================================================================
        // 3. ENVIO POR E-MAIL
        // =========================================================================
        JavaMailSenderImpl mailSender = criarCarteiroDinamico();
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        // Lendo diretamente da raiz (removido o getFiscal())
        helper.setFrom(config.getSmtpUsername() != null ? config.getSmtpUsername() : "naoresponda@ddcosmeticos.com.br");
        helper.setTo(emailDestino);
        helper.setSubject("Relatório Mensal de Performance - " + inicio.getMonth().name());
        helper.setText("Olá, Admin.\nSegue em anexo o resumo financeiro detalhado do mês anterior.");
        helper.addAttachment("Relatorio_DD_Cosmeticos_" + inicio.getMonth().name() + ".pdf",
                new ByteArrayResource(out.toByteArray()));

        mailSender.send(message);
        log.info("Relatório mensal enviado com sucesso para: {}", emailDestino);
    }
}