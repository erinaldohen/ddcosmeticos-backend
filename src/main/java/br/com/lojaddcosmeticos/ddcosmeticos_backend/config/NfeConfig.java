package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;

@Configuration
public class NfeConfig {

    private final ResourceLoader resourceLoader;

    // A senha deve ir para o application.properties em produção
    private static final String SENHA_CERTIFICADO = "912219";
    private static final String NOME_ARQUIVO_CERTIFICADO = "173771294_D_D_COSMETICOS_LTDA_57648950000144.pfx";

    public NfeConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public ConfiguracoesNfe iniciarConfiguracoesNfe() throws Exception {
        try {
            // 1. CARREGAR O CERTIFICADO DO CLASSPATH (Pasta resources)
            // Lendo como Stream para funcionar dentro do .jar ou Docker
            Resource resource = resourceLoader.getResource("classpath:" + NOME_ARQUIVO_CERTIFICADO);

            if (!resource.exists()) {
                throw new Exception("Certificado não encontrado em src/main/resources/" + NOME_ARQUIVO_CERTIFICADO);
            }

            InputStream entradaCertificado = resource.getInputStream();
            byte[] certificadoBytes = entradaCertificado.readAllBytes();

            // 2. CRIA O OBJETO CERTIFICADO DA BIBLIOTECA
            Certificado certificado = CertificadoService.certificadoPfxBytes(certificadoBytes, SENHA_CERTIFICADO);

            // 3. CAMINHO DOS SCHEMAS (Arquivos XSD)
            // Aponta para a pasta raiz do projeto + schemas.
            // Certifique-se de ter a pasta 'schemas' em src/main/resources/schemas
            String caminhoSchemas = System.getProperty("user.dir") + "/src/main/resources/schemas";

            // 4. CRIA A CONFIGURAÇÃO FINAL
            // Altere EstadosEnum.PE se a empresa não for de Pernambuco
            ConfiguracoesNfe config = ConfiguracoesNfe.criarConfiguracoes(
                    EstadosEnum.PE,
                    AmbienteEnum.HOMOLOGACAO,
                    certificado,
                    caminhoSchemas
            );

            System.out.println("--- CONFIGURAÇÃO NFE INICIADA: AMBIENTE HOMOLOGAÇÃO (PE) ---");
            return config;

        } catch (Exception e) {
            System.err.println("ERRO AO INICIAR CONFIGURAÇÃO NFE: " + e.getMessage());
            throw e;
        }
    }
}