package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.NfeConfig;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NfceService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private NfeConfig nfeConfig;

    @Value("${nfe.csc:TIMED-CSC-TOKEN}")
    private String cscToken;

    @Value("${nfe.csc-id:1}")
    private String cscId;

    // --- SOBRECARGA PARA COMPATIBILIDADE (Evita erro de "argument list differ") ---
    public NfceResponseDTO emitirNfce(Venda venda) {
        return emitirNfce(venda, false);
    }

    public NfceResponseDTO emitirNfce(Venda venda, boolean apenasFiscal) {
        // 1. Gerar Sequencial (Iniciando em 5714 se necessário)
        Long proximoNumero = gerarProximoNumeroNfce();

        // 2. Simular XML
        String chaveAcesso = gerarChaveAcesso(proximoNumero);
        String xmlAssinado = "<nfe><infNFeId=\"" + chaveAcesso + "\"> ... </infNFeId></nfe>";

        // 3. Atualizar Venda
        venda.setStatusFiscal(StatusFiscal.APROVADA);
        venda.setXmlNfce(xmlAssinado);

        // Persistir a venda atualizada
        vendaRepository.save(venda);

        return new NfceResponseDTO(
                chaveAcesso,
                proximoNumero.toString(),
                "1",
                "AUTORIZADA",
                "Autorizado o uso da NF-e",
                xmlAssinado,
                LocalDateTime.now()
        );
    }

    private Long gerarProximoNumeroNfce() {
        Long ultimoNumeroBanco = 0L;
        long ultimaEmitidaLegado = 5713L; // Regra de Negócio

        if (ultimoNumeroBanco <= ultimaEmitidaLegado) {
            return ultimaEmitidaLegado + 1; // 5714
        }
        return ultimoNumeroBanco + 1;
    }

    private String gerarChaveAcesso(Long numeroNota) {
        return "43" + LocalDateTime.now().getYear() + "00000000000000" + "55" + "001" + String.format("%09d", numeroNota) + "1" + "00000000";
    }
}