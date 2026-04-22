package br.com.lojaddcosmeticos.ddcosmeticos_backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SefazScheduler {

    // Injeta o SefazDistribuicaoService

    // Roda de 2 em 2 horas (A SEFAZ bloqueia CNPJs que fazem consultas de minuto a minuto)
    @Scheduled(cron = "0 0 */2 * * *")
    public void rotinaBuscaNfeSefaz() {
        try {
            // configuracaoNfe = carregarCertificadoDaEmpresa();
            // sefazDistribuicaoService.buscarNovasNotasNaSefaz(configuracaoNfe);
            System.out.println("Rotina SEFAZ executada com sucesso.");
        } catch (Exception e) {
            System.err.println("Erro ao buscar notas na SEFAZ: " + e.getMessage());
        }
    }
}