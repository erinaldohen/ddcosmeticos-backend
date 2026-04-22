package br.com.lojaddcosmeticos.ddcosmeticos_backend.scheduler;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.NfeConfig;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.SefazDistribuicaoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SefazScheduler {

    @Autowired
    private SefazDistribuicaoService sefazDistribuicaoService;

    @Autowired
    private NfeConfig nfeConfigBuilder;

    // 🔥 SURPRESA: Roda automaticamente todos os dias às 06h, 12h e 18h
    @Scheduled(cron = "0 0 6,12,18 * * *")
    public void rotinaBuscaNfeSefaz() {
        try {
            System.out.println("⏳ [ROBÔ SEFAZ] Acordando para buscar novas notas (Rotina Automática)...");

            // Força a leitura do certificado e do ambiente de Produção
            ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);

            // O Robô lê o CNPJ diretamente do certificado digital
            String cnpjEmpresa = config.getCertificado().getCnpjCpf();

            sefazDistribuicaoService.buscarNovasNotasNaSefaz(config, cnpjEmpresa);

            System.out.println("✅ [ROBÔ SEFAZ] Varredura concluída. Voltando a dormir.");
        } catch (Exception e) {
            System.err.println("❌ [ROBÔ SEFAZ] Falha na rotina automática: " + e.getMessage());
        }
    }
}