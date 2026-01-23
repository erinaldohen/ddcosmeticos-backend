package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.AmbienteFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe; // Import correto do objeto de configuração
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class NfceService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ConfiguracaoLojaRepository configuracaoLojaRepository;

    // Injeta o bean de configuração da lib (pode ser nulo se banco vazio)
    @Autowired(required = false)
    private ConfiguracoesNfe configuracoesNfe;

    // --- SOBRECARGA PARA COMPATIBILIDADE ---
    public NfceResponseDTO emitirNfce(Venda venda) {
        return emitirNfce(venda, false);
    }

    @Transactional
    public NfceResponseDTO emitirNfce(Venda venda, boolean apenasFiscal) {
        // Tenta recuperar dados reais do banco
        String cscToken = "TIMED-CSC-TOKEN"; // Valor default de fallback
        String cscId = "1";

        Optional<ConfiguracaoLoja> configOpt = configuracaoLojaRepository.findFirstByOrderByIdAsc();
        if (configOpt.isPresent()) {
            ConfiguracaoLoja config = configOpt.get();
            boolean isProducao = config.getAmbienteFiscal() == AmbienteFiscal.PRODUCAO;

            if (isProducao) {
                cscToken = config.getProducaoToken() != null ? config.getProducaoToken() : cscToken;
                cscId = config.getProducaoCscId() != null ? config.getProducaoCscId() : cscId;
            } else {
                cscToken = config.getHomologacaoToken() != null ? config.getHomologacaoToken() : cscToken;
                cscId = config.getHomologacaoCscId() != null ? config.getHomologacaoCscId() : cscId;
            }
        }

        // 1. Gerar Sequencial
        Long proximoNumero = gerarProximoNumeroNfce();

        // 2. Simular XML (Atualizado para refletir dados do banco na simulação)
        String chaveAcesso = gerarChaveAcesso(proximoNumero);

        // Simulação básica de XML para persistência
        String xmlAssinado = String.format(
                "<nfeProc versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\"><NFe><infNFe Id=\"%s\"><ide><nNF>%d</nNF><tpAmb>%s</tpAmb></ide></infNFe></NFe><protNFe><infProt><cStat>100</cStat><xMotivo>Autorizado o uso da NF-e (Simulacao)</xMotivo></infProt></protNFe></nfeProc>",
                "NFe" + chaveAcesso,
                proximoNumero,
                configOpt.map(c -> c.getAmbienteFiscal().toString()).orElse("HOMOLOGACAO")
        );

        // 3. Atualizar Venda
        venda.setStatusNfce(StatusFiscal.APROVADA);
        venda.setChaveAcessoNfce(xmlAssinado); // Mantendo comportamento original de salvar XML no campo de chave (embora o ideal fosse ter campo separado)

        // Persistir a venda atualizada
        vendaRepository.save(venda);

        return new NfceResponseDTO(
                chaveAcesso,
                proximoNumero.toString(),
                "1",
                "100",
                "Autorizado o uso da NF-e (Simulação)",
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
        // Ajustado Modelo para 65 (NFC-e) e UF padrão 26 (PE) caso necessário, ou mantendo 43 se for legado rigoroso
        // Usando formato padrão de chave de acesso
        return "26" + LocalDateTime.now().getYear() + "00000000000000" + "65" + "001" + String.format("%09d", numeroNota) + "1" + "00000000";
    }
}