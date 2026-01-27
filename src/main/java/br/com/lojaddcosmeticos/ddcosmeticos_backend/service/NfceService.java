package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.AmbienteFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class NfceService {

    @Autowired
    private ConfiguracaoLojaService configuracaoLojaService;

    @Autowired
    private VendaRepository vendaRepository;

    /**
     * Método responsável por orquestrar a emissão da NFC-e.
     * Este é um stub (esqueleto) atualizado para a nova estrutura de configuração.
     * Você deve integrar aqui sua biblioteca de NFe (ex: Samuel Olivera, Java NFe).
     */
    public NfceResponseDTO emitirNfce(Venda venda, boolean reenvio) {
        log.info("Iniciando emissão de NFC-e para Venda ID: {}", venda.getIdVenda());

        // 1. Busca Configurações Atualizadas
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();

        // CORREÇÃO 1: Acesso seguro aos dados fiscais embutidos
        if (config.getFiscal() == null) {
            throw new ValidationException("Configuração Fiscal não encontrada. Configure a loja primeiro.");
        }
        ConfiguracaoLoja.DadosFiscal dadosFiscal = config.getFiscal();

        // 2. Determina Ambiente e Credenciais (CSC/Token)
        String ambienteStr = dadosFiscal.getAmbiente(); // Agora é String: "HOMOLOGACAO" ou "PRODUCAO"
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

        if (cscId == null || cscToken == null) {
            throw new ValidationException("CSC ou Token não configurados para o ambiente: " + ambienteStr);
        }

        try {
            // =================================================================================
            // AQUI ENTRARIA A CHAMADA REAL PARA A BIBLIOTECA DE NFE
            // Exemplo fictício de lógica:
            //
            // ConfiguracoesNfe configNfe = ConfigFactory.cria(
            //      isProducao ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO,
            //      dadosFiscal.getCaminhoCertificado(),
            //      dadosFiscal.getSenhaCert()
            // );
            // TRetNFe retorno = Nfe.enviar(venda, configNfe);
            // =================================================================================

            // SIMULAÇÃO DE SUCESSO PARA O FLUXO DO SISTEMA
            String chaveAcessoSimulada = gerarChaveAcessoSimulada(venda, isProducao);
            String protocoloSimulado = String.valueOf(System.currentTimeMillis());

            // 3. Atualiza Venda com dados da Nota
            venda.setChaveAcessoNfce(chaveAcessoSimulada);
            venda.setProtocoloAutorizacao(protocoloSimulado);
            venda.setStatusNfce(StatusFiscal.AUTORIZADA);
            venda.setDataAutorizacao(LocalDateTime.now());
            venda.setXmlNota("<?xml version=\"1.0\" encoding=\"UTF-8\"?><nfe>Simulacao...</nfe>"); // Em produção, salvar o XML real

            vendaRepository.save(venda);

            return new NfceResponseDTO(
                    venda.getIdVenda(),
                    venda.getStatusNfce().name(),
                    "Autorizado o uso da NF-e",
                    chaveAcessoSimulada,
                    protocoloSimulado,
                    venda.getXmlNota(),
                    null // PDF URL (se houver)
            );

        } catch (Exception e) {
            log.error("Erro ao emitir NFC-e: ", e);

            // Tratamento de erro
            venda.setStatusNfce(StatusFiscal.ERRO_EMISSAO);
            venda.setMensagemRejeicao(e.getMessage());
            vendaRepository.save(venda);

            throw new ValidationException("Erro na emissão fiscal: " + e.getMessage());
        }
    }

    private String gerarChaveAcessoSimulada(Venda venda, boolean isProducao) {
        // Formato simplificado de chave de acesso (44 dígitos)
        // UF + AAMM + CNPJ + Mod + Serie + Num + FormaEmissao + CodNum + DV
        String prefix = isProducao ? "26" : "26"; // PE
        String cnpj = "00000000000000"; // Deveria vir de config.getLoja().getCnpj()
        return prefix + "2401" + cnpj + "65" + "001" + String.format("%09d", venda.getIdVenda()) + "1" + "12345678" + "0";
    }
}