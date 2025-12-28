package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class NfceService {

    @Autowired
    private VendaRepository vendaRepository;

    /**
     * Emite a NFC-e de forma assíncrona para não travar o PDV.
     * O status da venda será atualizado conforme o resultado.
     *
     * @param venda A venda realizada.
     * @param apenasComNfEntrada Regra de negócio para emissão condicional.
     */
    @Async
    @Transactional
    public void emitirNfce(Venda venda, boolean apenasComNfEntrada) {
        log.info("Iniciando emissão assíncrona de NFC-e para Venda #{}", venda.getId());

        try {
            // 1. Validações prévias (opcional, dependendo da regra de 'apenasComNfEntrada')
            if (apenasComNfEntrada && !verificarElegibilidade(venda)) {
                log.warn("Venda #{} não elegível para emissão (Regra de Entrada). Ignorando.", venda.getId());
                return;
            }

            // 2. Simulação de processamento pesado (comunicação com SEFAZ)
            // TODO: Aqui entra sua lógica real de geração de XML e envio para a SEFAZ/API externa.
            processarEnvioSefaz(venda);

            // 3. Sucesso
            venda.setStatusFiscal(StatusFiscal.APROVADA);
            vendaRepository.save(venda);
            log.info("NFC-e da Venda #{} emitida com SUCESSO.", venda.getId());

        } catch (Exception e) {
            // 4. Tratamento de Falha
            log.error("Erro ao emitir NFC-e para Venda #{}: {}", venda.getId(), e.getMessage());

            // É crucial atualizar o status para que o usuário saiba que falhou e possa tentar novamente
            try {
                // Recarrega a venda para garantir que temos a versão mais atual antes de salvar o erro
                Venda vendaErro = vendaRepository.findById(venda.getId()).orElse(venda);
                vendaErro.setStatusFiscal(StatusFiscal.ERRO_EMISSAO);
                vendaRepository.save(vendaErro);
            } catch (Exception ex) {
                log.error("Falha crítica ao atualizar status de erro da Venda #{}", venda.getId(), ex);
            }
        }
    }

    private boolean verificarElegibilidade(Venda venda) {
        // Implemente sua lógica de verificação aqui se necessário.
        // Ex: Verificar se todos os produtos têm NCM válido.
        return true;
    }

    private void processarEnvioSefaz(Venda venda) throws InterruptedException {
        // Simulação de delay de rede (remover em produção quando tiver a integração real)
        Thread.sleep(1000);
        // Lógica de integração vai aqui...
    }
}