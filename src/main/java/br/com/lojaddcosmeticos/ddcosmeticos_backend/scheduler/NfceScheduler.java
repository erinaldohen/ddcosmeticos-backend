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

    // Executa a cada 60 segundos APÓS o término da última execução
    @Scheduled(fixedDelay = 60000)
    public void processarNotasPendentesEContingencia() {
        // Busca notas que não foram autorizadas online
        List<Venda> notasParaProcessar = vendaRepository.findByStatusNfceIn(
                List.of(StatusFiscal.PENDENTE, StatusFiscal.CONTINGENCIA)
        );

        if (notasParaProcessar.isEmpty()) return;

        log.info("🔄 SCHEDULER: Processando {} notas pendentes ou em contingência...", notasParaProcessar.size());

        for (Venda venda : notasParaProcessar) {
            // Agora ambos os casos usam o mesmo fluxo de re-transmissão
            nfceService.transmitirNotaContingencia(venda);
        }
    }
}