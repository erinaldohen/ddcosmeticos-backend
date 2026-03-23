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

    // Aumentado para 3 minutos (180000 ms). Evita bloqueio da SEFAZ por "Consumo Indevido"
    @Scheduled(fixedDelay = 180000)
    public void processarNotasPendentesEContingencia() {
        try {
            // Busca notas que não foram autorizadas online
            List<Venda> notasParaProcessar = vendaRepository.findByStatusNfceIn(
                    List.of(StatusFiscal.PENDENTE, StatusFiscal.CONTINGENCIA)
            );

            if (notasParaProcessar.isEmpty()) return;

            // Segurança: Processar no máximo 30 notas por ciclo para não sobrecarregar a memória nem a SEFAZ
            List<Venda> lote = notasParaProcessar.stream().limit(30).toList();

            log.info("🔄 SCHEDULER: Encontradas {} notas pendentes. Processando lote de {} notas...",
                    notasParaProcessar.size(), lote.size());

            for (Venda venda : lote) {
                // Tenta retransmitir
                nfceService.transmitirNotaContingencia(venda);

                // Micropausa de 1 segundo entre envios para respeitar o limite de requisições da SEFAZ
                Thread.sleep(1000);
            }

            log.info("✅ SCHEDULER: Lote de retransmissão finalizado.");

        } catch (Exception e) {
            log.error("❌ SCHEDULER: Erro crítico durante a execução do lote de NFC-e: {}", e.getMessage());
        }
    }
}