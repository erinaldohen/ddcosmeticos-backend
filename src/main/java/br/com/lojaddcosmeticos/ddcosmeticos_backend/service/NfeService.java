package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class NfeService {

    @Autowired
    private VendaRepository vendaRepository;

    /**
     * Emite uma NF-e (Modelo 55) - Obrigatória para vendas interestaduais ou atacado.
     */
    @Transactional
    public NfceResponseDTO emitirNfeModelo55(Long idVenda) {
        Venda venda = vendaRepository.findByIdComItens(idVenda)
                .orElseThrow(() -> new ValidationException("Venda não encontrada."));

        // 1. Validações Específicas de NF-e
        validarDadosDestinatario(venda.getCliente());

        // 2. Numeração (Sequência independente da NFC-e)
        // Em produção, isso viria de uma tabela de sequências. Aqui simulamos.
        String numeroNota = String.valueOf(new Random().nextInt(100000) + 1);

        // 3. Gerar Chave de Acesso (Modelo 55)
        String chaveAcesso = gerarChaveAcesso(numeroNota);

        // 4. Simular XML (Na prática, usaria uma biblioteca de assinatura digital)
        String xmlSimulado = "<nfeProc versao=\"4.00\"><mod>55</mod><chNFe>" + chaveAcesso + "</chNFe></nfeProc>";

        // 5. Atualizar Venda
        venda.setStatusFiscal(StatusFiscal.APROVADA);
        venda.setXmlNfce(xmlSimulado); // Reutilizamos o campo XML para guardar a NF-e
        vendaRepository.save(venda);

        // Retorna DTO (Reutilizando o DTO de resposta fiscal)
        return new NfceResponseDTO(
                chaveAcesso,
                numeroNota,
                "1", // Série
                "AUTORIZADA",
                "Autorizado o uso da NF-e (Modelo 55)",
                xmlSimulado,
                LocalDateTime.now()
        );
    }

    private void validarDadosDestinatario(Cliente cliente) {
        if (cliente == null) {
            throw new ValidationException("Para emitir NF-e, a venda deve ter um cliente identificado.");
        }
        if (cliente.getDocumento() == null || cliente.getDocumento().length() < 11) {
            throw new ValidationException("CPF/CNPJ do cliente inválido para NF-e.");
        }
        if (cliente.getEndereco() == null || cliente.getEndereco().isBlank()) {
            throw new ValidationException("Endereço do cliente é obrigatório para NF-e.");
        }
    }

    private String gerarChaveAcesso(String numeroNota) {
        // Modelo 55 fixo na chave
        return "43" + LocalDateTime.now().getYear() + "00000000000000" + "55" + "001" + String.format("%09d", Long.parseLong(numeroNota)) + "1" + "00000000";
    }
}