package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.NfeConfig;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.SefazDistribuicaoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import jakarta.annotation.PostConstruct; // Importante
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class DdcosmeticosBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DdcosmeticosBackendApplication.class, args);
    }

    // --- ADICIONE ISTO AQUI ---
    @PostConstruct
    public void init() {
        // Força o sistema a rodar no horário de Brasília/Recife, independente do servidor
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }

    @Bean
    public CommandLineRunner testarMotorSefaz(SefazDistribuicaoService sefazService, NfeConfig nfeConfig) {
        return args -> {
            try {
                System.out.println("==============================================");
                System.out.println("🔥 TESTE DE CHOQUE: A ARRANCAR MOTOR SEFAZ...");

                // Força produção para ver notas reais
                ConfiguracoesNfe config = nfeConfig.construirConfiguracaoDinamica(true);
                String cnpjReal = config.getCertificado().getCnpjCpf();
                sefazService.buscarNovasNotasNaSefaz(config, cnpjReal);

                System.out.println("✅ TESTE DE CHOQUE CONCLUÍDO!");
                System.out.println("==============================================");
            } catch (Exception e) {
                System.err.println("❌ FALHA NO TESTE DE CHOQUE:");
                e.printStackTrace();
                System.out.println("==============================================");
            }
        };
    }
}