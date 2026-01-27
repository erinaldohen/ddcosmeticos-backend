package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.util.Optional;

@Configuration
public class NfeConfig {

    private final ResourceLoader resourceLoader;
    private final ConfiguracaoLojaRepository configuracaoLojaRepository;

    @Value("${nfe.certificado.senha:912219}")
    private String senhaCertificado;

    private static final String NOME_ARQUIVO_CERTIFICADO = "173771294_D_D_COSMETICOS_LTDA_57648950000144.pfx";

    public NfeConfig(ResourceLoader resourceLoader, ConfiguracaoLojaRepository configuracaoLojaRepository) {
        this.resourceLoader = resourceLoader;
        this.configuracaoLojaRepository = configuracaoLojaRepository;
    }

    @Bean
    public ConfiguracoesNfe iniciarConfiguracoesNfe() {
        try {
            // 1. TENTA BUSCAR NO BANCO
            Optional<ConfiguracaoLoja> configLojaOpt = configuracaoLojaRepository.findFirstByOrderByIdAsc();

            if (configLojaOpt.isEmpty()) {
                System.out.println("--- AVISO: Nenhuma configuração de loja encontrada. Módulo NFe iniciará DESATIVADO. ---");
                return null;
            }

            ConfiguracaoLoja configLoja = configLojaOpt.get();

            // 2. DEFINIR O AMBIENTE (CORRIGIDO)
            AmbienteEnum ambiente = AmbienteEnum.HOMOLOGACAO;
            // Verifica se o objeto fiscal existe e acessa o ambiente (agora String)
            if (configLoja.getFiscal() != null && "PRODUCAO".equalsIgnoreCase(configLoja.getFiscal().getAmbiente())) {
                ambiente = AmbienteEnum.PRODUCAO;
            }

            // 3. DEFINIR O ESTADO (CORRIGIDO)
            EstadosEnum estado = EstadosEnum.PE; // Default
            // Verifica se o objeto endereco existe e acessa a UF
            if (configLoja.getEndereco() != null && configLoja.getEndereco().getUf() != null) {
                try {
                    estado = EstadosEnum.valueOf(configLoja.getEndereco().getUf().toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.err.println("UF inválida: " + configLoja.getEndereco().getUf() + ". Usando PE.");
                }
            }

            // 4. CARREGAR CERTIFICADO
            Resource resource = resourceLoader.getResource("classpath:" + NOME_ARQUIVO_CERTIFICADO);
            if (!resource.exists()) {
                System.err.println("AVISO: Certificado PFX não encontrado. NFe desativada.");
                return null;
            }

            InputStream entradaCertificado = resource.getInputStream();
            byte[] certificadoBytes = entradaCertificado.readAllBytes();
            Certificado certificado = CertificadoService.certificadoPfxBytes(certificadoBytes, senhaCertificado);

            // 5. SCHEMAS
            String caminhoSchemas = System.getProperty("user.dir") + "/src/main/resources/schemas";

            // 6. RETORNA A CONFIGURAÇÃO PRONTA
            return ConfiguracoesNfe.criarConfiguracoes(
                    estado,
                    ambiente,
                    certificado,
                    caminhoSchemas
            );

        } catch (Exception e) {
            System.err.println("ERRO NÃO FATAL AO INICIAR NFE: " + e.getMessage());
            return null;
        }
    }
}