package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.NfeConfig;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.SefazDistribuicaoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableCaching
public class DdcosmeticosBackendApplication {

    @Autowired
    private SefazDistribuicaoService sefazDistribuicaoService;

    @Autowired
    private NfeConfig nfeConfigBuilder;

    public static void main(String[] args) {

        // 🔥 A SOLUÇÃO DEFINITIVA PARA A SEFAZ PE 🔥
        // 1. Força a utilização exclusiva do TLSv1.2 (rejeita o 1.3 que a SEFAZ não suporta bem)
        System.setProperty("https.protocols", "TLSv1.2");
        System.setProperty("jdk.tls.client.protocols", "TLSv1.2");

        // 2. Desliga a extensão SNI (Server Name Indication)
        // Os Load Balancers antigos da SEFAZ rejeitam conexões Java modernas que enviam SNI,
        // resultando no temido erro "Connection or outbound has closed".
        System.setProperty("jsse.enableSNIExtension", "false");

        SpringApplication.run(DdcosmeticosBackendApplication.class, args);
    }

    // --- TESTE DE CHOQUE ---
    @Bean
    public CommandLineRunner testarMotorSefaz() {
        return args -> {
            System.out.println("==============================================");
            System.out.println("🔥 TESTE DE CHOQUE: A ARRANCAR MOTOR SEFAZ...");
            try {
                ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);
                String cnpjEmpresa = config.getCertificado().getCnpjCpf();

                sefazDistribuicaoService.buscarNovasNotasNaSefaz(config, cnpjEmpresa);

                System.out.println("✅ TESTE BEM SUCEDIDO!");
            } catch (Exception e) {
                System.out.println("❌ FALHA NO TESTE DE CHOQUE:");
                e.printStackTrace();
            }
            System.out.println("==============================================");
        };
    }
}