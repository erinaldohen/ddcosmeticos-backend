package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SplitPaymentInstructionDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoDestinatarioSplit;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PagamentoGatewayService {

    @Autowired
    private SplitPaymentService splitPaymentService;

    /**
     * Processa a venda e executa a partilha real dos valores (Split Payment).
     */
    public void processarPagamentoComSplit(Venda venda) {
        log.info("Iniciando processamento financeiro para Venda #{}", venda.getId());

        // Gera as instruções baseadas na inteligência fiscal da LC 214
        List<SplitPaymentInstructionDTO> instrucoes = splitPaymentService.gerarInstrucoesSplit(venda);

        // Executa a partilha conforme o tipo de destinatário
        for (SplitPaymentInstructionDTO instrucao : instrucoes) {
            executarTransferenciaEspecifica(instrucao);
        }

        log.info("Processo de Split Payment concluído para Venda #{}", venda.getId());
    }

    /**
     * Lógica de Switch para tratar cada ente da Reforma Tributária.
     */
    private void executarTransferenciaEspecifica(SplitPaymentInstructionDTO instrucao) {
        switch (instrucao.destinatarioTipo()) {
            case UNIAO_FEDERAL -> {
                log.info("[GATEWAY - UNIÃO] Enviando CBS/Seletivo para Conta Única do Tesouro: R$ {}", instrucao.valor());
                // Aqui: gateway.enviarPix(contaUniao, instrucao.valor());
            }
            case ESTADO_MUNICIPIO -> {
                log.info("[GATEWAY - ESTADO/MUN] Enviando IBS para Comitê Gestor: R$ {}", instrucao.valor());
                // Aqui: gateway.enviarPix(contaComiteGestor, instrucao.valor());
            }
            case LOJISTA -> {
                log.info("[GATEWAY - LOJISTA] Depositando Valor Líquido na conta do cliente: R$ {}", instrucao.valor());
                // Aqui: gateway.transferirParaContaLojista(instrucao.valor());
            }
            case MARKETPLACE -> {
                log.info("[GATEWAY - TAXA] Retendo comissão de plataforma: R$ {}", instrucao.valor());
            }
            default -> throw new IllegalArgumentException("Tipo de destinatário desconhecido: " + instrucao.destinatarioTipo());
        }
    }
}