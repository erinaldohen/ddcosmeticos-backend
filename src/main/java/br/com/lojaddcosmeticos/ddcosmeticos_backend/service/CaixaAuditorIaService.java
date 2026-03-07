package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaixaAuditorIaService {

    private final CaixaDiarioRepository caixaRepository;

    // Se fosse usar uma API real, você injetaria o RestTemplate ou WebClient aqui
    // e a chave da API viria do application.properties

    @Async // A MÁGICA: Isso faz o método rodar em uma thread separada, sem travar o PDV!
    @Transactional
    public void auditarQuebraDeCaixa(Long caixaId, String nomeOperador, String justificativa) {
        log.info("Iniciando auditoria IA em background para o caixa #{}", caixaId);

        try {
            // 1. Busca os dados frescos do banco
            CaixaDiario caixa = caixaRepository.findById(caixaId).orElseThrow();

            // 2. Monta o contexto perfeito (Engenharia de Prompt)
            String prompt = String.format(
                    "Você é um auditor financeiro sênior de varejo analisando um fechamento de caixa da loja DD Cosméticos, localizada em Recife. " +
                            "O operador '%s' fechou o turno com uma quebra de R$ %s. " +
                            "O sistema esperava R$ %s, mas havia apenas R$ %s na gaveta física. " +
                            "A justificativa dada pelo operador foi: '%s'. " +
                            "Faça uma análise rigorosa e direta (máximo de 3 linhas): A justificativa faz sentido financeiramente para o valor exato que está faltando? Existe risco de fraude ou erro operacional crônico? Dê uma recomendação para o administrador.",
                    nomeOperador,
                    caixa.getDiferencaCaixa(),
                    caixa.getSaldoEsperadoSistema(),
                    caixa.getValorFisicoInformado(),
                    justificativa != null ? justificativa : "Nenhuma justificativa fornecida."
            );

            // 3. Simulação da chamada HTTP para a API da IA (Gemini/OpenAI)
            // String respostaIA = restTemplate.postForObject(apiURL, promptRequest, String.class);

            // Simulação do retorno da IA para testes:
            String respostaIA = simularRespostaIA(caixa.getDiferencaCaixa().doubleValue(), justificativa);

            // 4. Salva o veredito da IA no banco de dados
            caixa.setAnaliseAuditoriaIa(respostaIA);
            caixaRepository.save(caixa);

            log.info("Auditoria IA concluída com sucesso para o caixa #{}", caixaId);

            // Aqui você poderia disparar um e-mail com JavaMailSender caso a IA detecte algo grave!

        } catch (Exception e) {
            log.error("Falha ao auditar caixa com IA: {}", e.getMessage());
        }
    }

    // Mock temporário enquanto você não coloca sua chave de API
    private String simularRespostaIA(double diferenca, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            return "ALERTA: Nenhuma justificativa foi fornecida pelo operador para uma falta de dinheiro. Recomendada verificação imediata das câmeras e contagem de cofre.";
        }
        if (justificativa.toLowerCase().contains("troco") && diferenca > -5.0) {
            return "ANÁLISE: A justificativa de erro de troco é plausível para o valor baixo de quebra. Risco de fraude baixo. Recomendado treinar o operador na entrega de moedas.";
        }
        return "ALERTA: A justificativa fornecida ('" + justificativa + "') não justifica matematicamente o sumiço do valor. Incompatibilidade detectada. Exige auditoria manual do administrador.";
    }
}