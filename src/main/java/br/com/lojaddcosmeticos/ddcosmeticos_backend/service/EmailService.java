package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String remetente;

    public void enviarRelatorioAdmin(byte[] anexoPdf, String nomeArquivo, String destinatario) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(remetente);
            helper.setTo(destinatario);
            helper.setSubject("📊 Relatório Financeiro Mensal - DD Cosméticos");
            helper.setText("Olá Administrador,\n\nSegue em anexo o relatório financeiro consolidado referente ao último mês de operação da sua loja.\n\nAtenciosamente,\nSistema DD Cosméticos");

            helper.addAttachment(nomeArquivo, new ByteArrayResource(anexoPdf), "application/pdf");

            mailSender.send(message);
            log.info("📧 Relatório enviado com sucesso para: {}", destinatario);

        } catch (Exception e) {
            log.error("❌ Erro ao enviar e-mail com relatório: {}", e.getMessage());
            throw new RuntimeException("Falha no Servidor de E-mail (SMTP): " + e.getMessage());
        }
    }
}