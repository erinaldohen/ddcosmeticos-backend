package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final ConfiguracaoLojaService configuracaoLojaService;

    private JavaMailSenderImpl criarCarteiroDinamico() {
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // 🚨 CORREÇÃO: Lendo direto da raiz da configuração (sem getFiscal)
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

    public void enviarEmail(String destinatario, String assunto, String corpo) {
        try {
            JavaMailSenderImpl mailSender = criarCarteiroDinamico();

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();

            // 🚨 CORREÇÃO: Lendo direto da raiz
            helper.setFrom(config.getSmtpUsername() != null ? config.getSmtpUsername() : "naoresponda@ddcosmeticos.com.br");
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(corpo);

            mailSender.send(message);
            log.info("E-mail genérico enviado com sucesso para {}", destinatario);

        } catch (Exception e) {
            log.error("Erro ao enviar e-mail para {}: {}", destinatario, e.getMessage());
            throw new ValidationException("Falha ao enviar e-mail. Verifique as configurações de SMTP da loja.");
        }
    }

    public void enviarRelatorioAdmin(byte[] relatorioPdf, String emailDestino, String assunto) {
        try {
            JavaMailSenderImpl mailSender = criarCarteiroDinamico();

            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");

            ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();

            // 🚨 CORREÇÃO: Lendo direto da raiz
            helper.setFrom(config.getSmtpUsername() != null ? config.getSmtpUsername() : "naoresponda@ddcosmeticos.com.br");
            helper.setTo(emailDestino);
            helper.setSubject(assunto);
            helper.setText("Olá,\n\nSegue em anexo o relatório solicitado gerado pelo sistema.\n\nAtenciosamente,\nDD Cosméticos");

            org.springframework.core.io.ByteArrayResource pdfAttachment = new org.springframework.core.io.ByteArrayResource(relatorioPdf);
            helper.addAttachment("Relatorio_DDCosmeticos.pdf", pdfAttachment);

            mailSender.send(message);
            log.info("Relatório PDF enviado com sucesso para {}", emailDestino);

        } catch (Exception e) {
            log.error("Erro ao enviar relatório para {}: {}", emailDestino, e.getMessage());
            throw new ValidationException("Falha ao enviar o relatório por e-mail. Verifique as configurações de SMTP no painel.");
        }
    }
}