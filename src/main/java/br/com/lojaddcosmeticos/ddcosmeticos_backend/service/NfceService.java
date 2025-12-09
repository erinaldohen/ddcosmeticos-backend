// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/service/NfceService.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyStore;

/**
 * Serviço responsável pela geração, assinatura e transmissão da Nota Fiscal de Consumidor Eletrônica (NFC-e).
 * Utiliza o KeyStore injetado com o certificado digital.
 */
@Service
public class NfceService {

    // O KeyStore injetado do NfeConfig.java
    @Autowired
    private KeyStore keyStoreNfe;

    /**
     * Processa a venda para gerar, assinar e transmitir a NFC-e.
     * Esta é uma simulação da lógica que faria a comunicação real com a SEFAZ
     * (seria aqui que a biblioteca fiscal seria utilizada).
     *
     * @param vendaRegistrada A entidade Venda já persistida.
     * @return O DTO de resposta fiscal.
     */
    public NfceResponseDTO emitirNfce(Venda vendaRegistrada) {

        // 1. Lógica de Geração do XML (SIMULAÇÃO)
        String xmlGerado = "";

        // 2. Assinatura do XML (Usando o KeyStore injetado)
        // Aqui o KeyStore seria usado para obter a chave privada e assinar o XML.
        // if (keyStoreNfe != null) { /* Lógica de Assinatura */ }

        // 3. Transmissão para a SEFAZ
        // Aqui a biblioteca fiscal faria a chamada ao Web Service.

        // 4. Retorno (SIMULAÇÃO DE SUCESSO)
        System.out.println("--- NFC-e SIMULADA: Enviando venda ID " + vendaRegistrada.getId() + " para a SEFAZ...");

        // Usamos o Builder do Lombok (@Builder no DTO)
        return NfceResponseDTO.builder()
                .chaveDeAcesso("43250100000000000000000000000000000000000000") // Chave fictícia
                .numeroNota("000001")
                .statusSefaz("Autorizado o uso da NFC-e. Transação ID: " + vendaRegistrada.getId())
                .autorizada(true)
                .xmlNfce(xmlGerado)
                .build();
    }
}