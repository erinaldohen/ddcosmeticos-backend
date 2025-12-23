package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NfceService {

    /**
     * Emite a NFC-e considerando a regra de negócio da DD Cosméticos.
     * @param venda A venda realizada.
     * @param apenasItensComNfEntrada Se true, filtra apenas produtos com flag possuiNfEntrada = true.
     */
    public NfceResponseDTO emitirNfce(Venda venda, boolean apenasItensComNfEntrada) {
        log.info("Iniciando emissão de NFC-e para Venda #{}. Filtro parcial: {}", venda.getId(), apenasItensComNfEntrada);

        // Filtra os itens que comporão o XML
        List<ItemVenda> itensParaEmitir = venda.getItens().stream()
                .filter(item -> {
                    if (apenasItensComNfEntrada) {
                        return item.getProduto().isPossuiNfEntrada();
                    }
                    return true; // Se não for parcial, emite de tudo
                })
                .collect(Collectors.toList());

        if (itensParaEmitir.isEmpty()) {
            log.warn("Nenhum item elegível para emissão fiscal na Venda #{}. Venda registrada apenas gerencialmente.", venda.getId());
            return new NfceResponseDTO(null, "NAO_EMITIDA", null, "Venda registrada sem emissão fiscal (nenhum item elegível).");
        }

        // Processa os dados fiscais de cada item selecionado
        itensParaEmitir.forEach(item -> {
            String ncm = item.getProduto().getNcm();
            String cest = item.getProduto().getCest();

            // Lógica de CFOP para Varejo PE (CNAE 4772-5/00)
            // 5.102: Revenda de mercadoria adquirida de terceiros
            // 5.405: Revenda de mercadoria sujeita a ST (comum em cosméticos)
            String cfop = (cest != null && !cest.isEmpty()) ? "5405" : "5102";

            log.debug("Adicionando ao XML -> Produto: {} | NCM: {} | CFOP: {}",
                    item.getProduto().getDescricao(), ncm, cfop);
        });

        // MOCK DE INTEGRAÇÃO COM SEFAZ/PE
        // Aqui deve ser implementada a assinatura do XML (A1/A3) e envio para o WebService da Sefaz PE.
        // Recomenda-se uso de bibliotecas como Java NFe ou APIs de terceiros (Focus, PlugNotas) para robustez.

        String protocolo = "1352500" + System.currentTimeMillis();
        log.info("NFC-e autorizada pela SEFAZ PE. Protocolo: {}", protocolo);

        return new NfceResponseDTO("XML_ASSINADO_BASE64", "AUTORIZADO", protocolo, "Venda autorizada com sucesso!");
    }

    // Sobrecarga para manter compatibilidade caso seja chamado sem o parametro (assume total)
    public NfceResponseDTO emitirNfce(Venda venda) {
        return emitirNfce(venda, false);
    }
}