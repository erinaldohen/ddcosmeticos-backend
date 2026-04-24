package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.NfeConfig;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.SefazDistribuicaoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DdcosmeticosBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DdcosmeticosBackendApplication.class, args);
    }

    @Bean
    CommandLineRunner testarMotorSefaz(NfeConfig nfeConfig, SefazDistribuicaoService sefazService) {
        return args -> {
            System.out.println("==============================================");
            System.out.println("🔥 TESTE DE CHOQUE: A ARRANCAR MOTOR SEFAZ...");
            try {
                // Tentamos construir a configuração. Se falhar (ex: banco vazio), cai no catch e a aplicação continua
                ConfiguracoesNfe config = nfeConfig.construirConfiguracaoDinamica(true);
                String cnpj = config.getCertificado().getCnpjCpf();
                sefazService.buscarNovasNotasNaSefaz(config, cnpj);
                System.out.println("✅ TESTE BEM SUCEDIDO!");
            } catch (Exception e) {
                System.out.println("❌ TESTE IGNORADO (Configuração Fiscal ausente ou falha de conexão).");
                System.out.println("Detalhe: " + e.getMessage());
                // Importante: Não lançamos o erro de novo, senão a aplicação não sobe!
            }
            System.out.println("==============================================");
        };
    }
}