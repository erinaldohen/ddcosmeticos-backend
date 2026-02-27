package br.com.lojaddcosmeticos.ddcosmeticos_backend.scheduler;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NfceScheduler {

    private final VendaRepository vendaRepository;
    private final NfceService nfceService;

    // Roda a cada 1 minuto (60000 ms) para atuar como Fila de Recuperação (DLQ)
    @Scheduled(fixedDelay = 60000)
    public void processarNotasPendentesEContingencia() {
        List<Venda> notasParaProcessar = vendaRepository.findByStatusNfceIn(
                Arrays.asList(StatusFiscal.PENDENTE, StatusFiscal.CONTINGENCIA)
        );

        if (!notasParaProcessar.isEmpty()) {
            log.info("🔄 SCHEDULER: Encontradas {} notas pendentes/contingência. A iniciar transmissão de recuperação...", notasParaProcessar.size());

            for (Venda venda : notasParaProcessar) {
                try {
                    if (venda.getStatusNfce() == StatusFiscal.CONTINGENCIA) {
                        nfceService.transmitirNotaContingencia(venda);
                    } else {
                        nfceService.emitirNfce(venda); // Tenta emitir a pendente que ficou presa
                    }
                } catch (Exception e) {
                    log.error("❌ Erro no Scheduler ao processar nota da venda {}: {}", venda.getIdVenda(), e.getMessage());
                }
            }
        }
    }
}