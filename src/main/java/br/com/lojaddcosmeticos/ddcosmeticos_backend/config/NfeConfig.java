// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/config/NfeConfig.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.security.KeyStore;

/**
 * Configuração responsável por carregar o certificado digital (arquivo .pfx)
 * no contexto do Spring. Este KeyStore será usado por bibliotecas fiscais
 * para assinar e transmitir documentos como NF-e e NFC-e.
 */
@Configuration
public class NfeConfig {

    private final ResourceLoader resourceLoader;

    // A senha do certificado deve ser configurada via application.properties em produção,
    // mas será definida aqui para simplificar a etapa inicial.
    private static final String SENHA_CERTIFICADO = "912219"; // <<-- SUBSTITUA PELA SENHA REAL

    /**
     * Construtor que injeta o ResourceLoader do Spring.
     * @param resourceLoader Injetado automaticamente pelo Spring para carregar recursos.
     */
    public NfeConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Carrega o arquivo .pfx (certificado digital) como um KeyStore.
     * @return Um objeto KeyStore com o certificado carregado.
     * @throws Exception Se houver erro ao carregar o arquivo ou senha incorreta.
     */
    @Bean
    public KeyStore keyStoreNfe() throws Exception {
        // O nome do arquivo deve ser o que você carregou. Presume-se que esteja no classpath (src/main/resources).
        String certificadoPath = "173771294_D_D_COSMETICOS_LTDA_57648950000144.pfx";

        // Obtém o arquivo PFX do classpath
        try (InputStream inputStream = resourceLoader.getResource("classpath:" + certificadoPath).getInputStream()) {

            // Cria uma instância do KeyStore PKCS12
            KeyStore keyStore = KeyStore.getInstance("PKCS12");

            // Carrega o KeyStore com a senha
            keyStore.load(inputStream, SENHA_CERTIFICADO.toCharArray());

            System.out.println("--- CERTIFICADO DIGITAL CARREGADO COM SUCESSO. ---");
            return keyStore;

        } catch (Exception e) {
            System.err.println("ERRO CRÍTICO AO CARREGAR CERTIFICADO DIGITAL: " + e.getMessage());
            System.err.println("Verifique se o arquivo PFX está na pasta 'resources' e se a SENHA está correta.");
            throw e; // Lança a exceção para impedir a inicialização da aplicação
        }
    }
}