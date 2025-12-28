package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NfceService {

    @Autowired
    private VendaRepository vendaRepository;

    /**
     * Emite a NFC-e (Simulação DEV).
     * Mantém a assinatura original para compatibilidade com Controllers.
     */
    public NfceResponseDTO emitirNfce(Venda venda, boolean apenasItensComNfEntrada) {
        log.info("[DEV] Iniciando emissão de NFC-e para Venda #{}. Filtro parcial: {}", venda.getId(), apenasItensComNfEntrada);

        // 1. Lógica de Negócio (Preservada): Filtra itens elegíveis
        List<ItemVenda> itensParaEmitir = venda.getItens().stream()
                .filter(item -> {
                    if (apenasItensComNfEntrada) {
                        return item.getProduto().isPossuiNfEntrada();
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (itensParaEmitir.isEmpty()) {
            log.warn("Nenhum item elegível para emissão fiscal na Venda #{}.", venda.getId());
            return new NfceResponseDTO(null, "NAO_EMITIDA", null, "Venda registrada sem emissão fiscal (nenhum item elegível).");
        }

        try {
            // 2. Simulação de Processamento (Sefaz)
            // Em produção, aqui entraria a montagem do XML e envio real.
            Thread.sleep(500); // Simula delay de rede

            // 3. Persistência do Sucesso (CRÍTICO: O VendaService espera que alguém atualize o status)
            venda.setStatusFiscal(StatusFiscal.APROVADA);
            vendaRepository.save(venda);

            String protocolo = "DEV" + System.currentTimeMillis();
            log.info("[DEV] NFC-e autorizada. Protocolo: {}", protocolo);

            return new NfceResponseDTO("XML_SIMULADO_BASE64", "AUTORIZADO", protocolo, "Venda autorizada com sucesso (Ambiente DEV)!");

        } catch (Exception e) {
            log.error("Erro na emissão simulada: {}", e.getMessage());

            venda.setStatusFiscal(StatusFiscal.ERRO_EMISSAO);
            vendaRepository.save(venda); // Salva o erro para o usuário ver

            return new NfceResponseDTO(null, "ERRO", null, "Erro na comunicação com a SEFAZ (Simulado).");
        }
    }

    // Sobrecarga (Preservada para compatibilidade)
    public NfceResponseDTO emitirNfce(Venda venda) {
        return emitirNfce(venda, false);
    }
}