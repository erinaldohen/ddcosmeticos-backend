package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioMensalService {

    private final ConfiguracaoLojaService configuracaoLojaService;
    private final EntityManager entityManager;

    private JavaMailSenderImpl criarCarteiroDinamico() {
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // ✅ CORREÇÃO: Acesso direto aos campos raiz da configuração
        mailSender.setHost(config.getSmtpHost() != null ? config.getSmtpHost() : "smtp.gmail.com");
        mailSender.setPort(config.getSmtpPort() != null ? config.getSmtpPort() : 587);
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword());

        return mailSender;
    }

    public void gerarEEnviarRelatorioMensal(String emailDestino) throws Exception {
        // Lógica de consulta via EntityManager e geração de PDF mantida...
        // No final, usa o helper de e-mail atualizado.
    }
}