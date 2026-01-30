package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class NfceService {

    @Autowired
    private ConfiguracaoLojaService configuracaoLojaService;

    @Autowired
    private VendaRepository vendaRepository;

    /**
     * Método responsável por orquestrar a emissão da NFC-e.
     */
    public NfceResponseDTO emitirNfce(Venda venda, boolean reenvio) {
        log.info("Iniciando emissão de NFC-e para Venda ID: {}", venda.getIdVenda());

        // 1. Busca Configurações Atualizadas do Banco
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();

        // Acesso seguro aos dados fiscais (agora dentro do Embeddable DadosFiscal)
        if (config.getFiscal() == null) {
            throw new ValidationException("Configuração Fiscal não encontrada. Acesse o menu Configurações > Fiscal.");
        }
        ConfiguracaoLoja.DadosFiscal dadosFiscal = config.getFiscal();

        // 2. Determina Ambiente e Credenciais (CSC/Token)
        String ambienteStr = dadosFiscal.getAmbiente() != null ? dadosFiscal.getAmbiente() : "HOMOLOGACAO";
        boolean isProducao = "PRODUCAO".equalsIgnoreCase(ambienteStr);

        String cscId;
        String cscToken;

        if (isProducao) {
            cscId = dadosFiscal.getCscIdProducao();
            cscToken = dadosFiscal.getTokenProducao();
        } else {
            cscId = dadosFiscal.getCscIdHomologacao();
            cscToken = dadosFiscal.getTokenHomologacao();
        }

        // Validação de Credenciais Básicas
        if (cscId == null || cscId.isBlank() || cscToken == null || cscToken.isBlank()) {
            throw new ValidationException("CSC (ID e Token) não configurados para o ambiente: " + ambienteStr);
        }

        // 3. Preparação dos Dados do Responsável Técnico (CSRT)
        // Nota: Em PE, se estiver vazio, o sistema deve ignorar e não falhar.
        String csrtId = dadosFiscal.getCsrtId();
        String csrtHash = dadosFiscal.getCsrtHash();
        boolean temCsrt = csrtId != null && !csrtId.isBlank() && csrtHash != null && !csrtHash.isBlank();

        log.info("Ambiente: {} | CSRT Configurado: {}", ambienteStr, temCsrt ? "SIM" : "NÃO (Opcional em alguns estados)");

        try {
            // =================================================================================
            // INTEGRAÇÃO COM BIBLIOTECA DE NFE (Ex: Java NFe / NFePHP)
            // =================================================================================
            /*
             * Exemplo de montagem real (pseudocódigo):
             * * NFeConfig configNfe = new NFeConfig();
             * configNfe.setAmbiente(isProducao ? TAmb.PROD : TAmb.HOM);
             * configNfe.setCertificado(dadosFiscal.getCaminhoCertificado(), dadosFiscal.getSenhaCert());
             * * NFe nfe = new NFe();
             * // ... popula dados da venda ...
             * * // Adiciona CSRT se existir
             * if (temCsrt) {
             * nfe.setInfRespTec(new InfRespTec(csrtId, csrtHash));
             * }
             * * RetornoNfe retorno = NfeWs.enviar(nfe);
             */
            // =================================================================================

            // 4. SIMULAÇÃO DE SUCESSO (Para teste do fluxo sem certificado real)
            String chaveAcessoSimulada = gerarChaveAcessoSimulada(venda, isProducao);
            String protocoloSimulado = String.valueOf(System.currentTimeMillis());

            // Simula um XML básico
            String xmlSimulado = String.format(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><nfeProc><chNFe>%s</chNFe><nProt>%s</nProt><status>AUTORIZADO</status></nfeProc>",
                    chaveAcessoSimulada, protocoloSimulado
            );

            // 5. Atualiza Venda com dados da Nota
            venda.setChaveAcessoNfce(chaveAcessoSimulada);
            venda.setProtocoloAutorizacao(protocoloSimulado);
            venda.setStatusNfce(StatusFiscal.AUTORIZADA);
            venda.setDataAutorizacao(LocalDateTime.now());
            venda.setXmlNota(xmlSimulado);

            vendaRepository.save(venda);

            return new NfceResponseDTO(
                    venda.getIdVenda(),
                    venda.getStatusNfce().name(),
                    "Autorizado o uso da NF-e (Simulação)",
                    chaveAcessoSimulada,
                    protocoloSimulado,
                    venda.getXmlNota(),
                    null // URL PDF seria gerada aqui
            );

        } catch (Exception e) {
            log.error("Erro crítico ao emitir NFC-e: ", e);

            // Tratamento de erro
            venda.setStatusNfce(StatusFiscal.ERRO_EMISSAO);
            venda.setMensagemRejeicao(e.getMessage());
            vendaRepository.save(venda);

            // Relança para o Controller tratar o HTTP Status
            throw new ValidationException("Falha na emissão fiscal: " + e.getMessage());
        }
    }

    private String gerarChaveAcessoSimulada(Venda venda, boolean isProducao) {
        // UF(2) + AAMM(4) + CNPJ(14) + Mod(2) + Serie(3) + Num(9) + Forma(1) + Cod(8) + DV(1)
        String uf = "26"; // Pernambuco
        String aamm = "2601";
        String cnpj = "00000000000000"; // Ideal: config.getLoja().getCnpj()
        String mod = "65";
        String serie = "001";
        String numero = String.format("%09d", venda.getIdVenda());
        String random = "12345678";

        return uf + aamm + cnpj + mod + serie + numero + "1" + random + "0";
    }
}