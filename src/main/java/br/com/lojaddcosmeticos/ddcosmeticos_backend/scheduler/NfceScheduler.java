package br.com.lojaddcosmeticos.ddcosmeticos_backend.scheduler;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.NfceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class NfceScheduler {

    private final VendaRepository vendaRepository;
    private final NfceService nfceService;

    // Roda a cada 5 minutos (300000 ms) para não sobrecarregar
    @Scheduled(fixedDelay = 300000)
    public void verificarNotasPendentes() {
        List<Venda> notasEmContingencia = vendaRepository.findByStatusNfce(StatusFiscal.CONTINGENCIA);

        if (!notasEmContingencia.isEmpty()) {
            log.info("🔄 SCHEDULER: Encontradas {} notas em contingência. Tentando transmitir...", notasEmContingencia.size());

            for (Venda venda : notasEmContingencia) {
                nfceService.transmitirNotaContingencia(venda);
            }
        }
    }
}