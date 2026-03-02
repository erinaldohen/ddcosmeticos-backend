package br.com.lojaddcosmeticos.ddcosmeticos_backend.infrastructure.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Converter
public class CryptoConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    // 1. Removemos completamente a chave padrão do código.
    // O Spring vai buscar isso obrigatoriamente das variáveis de ambiente ou properties.
    @Value("${app.security.db-secret-key}")
    private String secretKey;

    // 2. Validação no momento em que o Spring Boot liga (Fail-Fast)
    // Se a chave não for fornecida ou tiver o tamanho errado, a API nem liga.
    @PostConstruct
    public void validarChaveNaInicializacao() {
        if (secretKey == null || secretKey.isBlank() || "${app.security.db-secret-key}".equals(secretKey)) {
            throw new IllegalStateException("\n\n[SEGURANÇA CRÍTICA] Chave de criptografia do banco não encontrada! \n" +
                    "Configure a variável de ambiente APP_SECURITY_DB_SECRET_KEY ou adicione ao application.properties.\n");
        }
        if (secretKey.length() != 16 && secretKey.length() != 24 && secretKey.length() != 32) {
            throw new IllegalStateException("\n\n[SEGURANÇA CRÍTICA] A chave de criptografia informada tem " + secretKey.length() + " caracteres. \n" +
                    "O algoritmo AES exige EXATAMENTE 16, 24 ou 32 caracteres.\n");
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getEncoder().encodeToString(cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar dado sensível no banco", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return new String(cipher.doFinal(Base64.getDecoder().decode(dbData)), StandardCharsets.UTF_8);

        } catch (IllegalArgumentException e) {
            return dbData;
        } catch (Exception e) {
            return dbData;
        }
    }
}