package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO; // Importe o DTO
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
public class NfceService {

    public NfceResponseDTO emitirNfce(Venda venda) {
        log.info("Montando XML da NFC-e para a Venda #{}", venda.getId());

        // Para cada item da venda, extraímos os dados fiscais que vieram do seu Excel
        venda.getItens().forEach(item -> {
            String ncm = item.getProduto().getNcm();
            String cest = item.getProduto().getCest();
            String origem = item.getProduto().getOrigem(); // 0 ou 1

            // Lógica de CFOP para Recife/PE:
            // 5102: Venda normal (Tributado)
            // 5405: Venda com ST (Substituição Tributária)
            String cfop = (cest != null && !cest.isEmpty()) ? "5405" : "5102";

            log.debug("Item: {} | NCM: {} | CEST: {} | CFOP: {}",
                    item.getProduto().getDescricao(), ncm, cest, cfop);
        });

        // Aqui entraria a integração com o parceiro de mensageria (ex: FocusNFe, PlugNotas ou API própria)
        // Por enquanto, simulamos o sucesso:
        return new NfceResponseDTO("XML_ASSINADO_AQUI", "AUTORIZADO", "13525000...", "Venda autorizada com sucesso!");
    }
}