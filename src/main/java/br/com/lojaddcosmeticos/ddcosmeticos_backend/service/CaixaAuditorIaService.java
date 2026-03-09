package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaixaAuditorIaService {

    private final CaixaDiarioRepository caixaRepository;

    @Async
    @Transactional
    public void auditarQuebraDeCaixa(Long caixaId, String nomeOperador, String justificativa) {
        log.info("Iniciando auditoria COMPORTAMENTAL IA em background para o caixa #{}", caixaId);

        try {
            Thread.sleep(2000); // Aguarda o fechamento salvar no banco

            CaixaDiario caixa = caixaRepository.findById(caixaId).orElseThrow();
            double diferenca = caixa.getDiferencaCaixa() != null ? caixa.getDiferencaCaixa().doubleValue() : 0.0;

            // ====================================================================
            // A MÁGICA CONTRA A FRAUDE: BUSCAR O HISTÓRICO RECENTE DO OPERADOR
            // ====================================================================
            LocalDateTime trintaDiasAtras = LocalDateTime.now().minusDays(30);

            // Busca todos os caixas e filtra (simplificação didática para não alterar o Repository)
            List<CaixaDiario> historicoOperador = caixaRepository.findAll().stream()
                    .filter(c -> c.getUsuarioAbertura() != null && c.getUsuarioAbertura().getId().equals(caixa.getUsuarioAbertura().getId()))
                    .filter(c -> c.getDataFechamento() != null && c.getDataFechamento().isAfter(trintaDiasAtras))
                    .filter(c -> c.getDiferencaCaixa() != null && c.getDiferencaCaixa().compareTo(BigDecimal.ZERO) < 0)
                    .toList();

            int ocorrenciasMes = historicoOperador.size();
            double valorTotalPerdido = historicoOperador.stream()
                    .mapToDouble(c -> c.getDiferencaCaixa().doubleValue())
                    .sum();

            // ====================================================================
            // ENGENHARIA DE PROMPT COM CONTEXTO COMPORTAMENTAL
            // ====================================================================
            String prompt = String.format(
                    "Você é um auditor financeiro sênior. Analise este fechamento:\n" +
                            "- Operador: %s\n" +
                            "- Quebra Atual: R$ %.2f\n" +
                            "- Justificativa dada: '%s'\n\n" +
                            "CONTEXTO HISTÓRICO (Últimos 30 dias):\n" +
                            "- Este operador já teve %d quebras registradas no mês.\n" +
                            "- O prejuízo acumulado por este operador é de R$ %.2f.\n\n" +
                            "REGRA OBRIGATÓRIA: Inicie sua resposta EXATAMENTE com uma destas tags: [RISCO: BAIXO], [RISCO: MEDIO] ou [RISCO: ALTO]. " +
                            "Atenção: Se houver recorrência no histórico, uma justificativa de erro comum (ex: falta de troco) perde totalmente a credibilidade e indica fraude formiguinha.",
                    nomeOperador, diferenca,
                    justificativa != null && !justificativa.isBlank() ? justificativa : "Nenhuma justificativa fornecida",
                    ocorrenciasMes, Math.abs(valorTotalPerdido)
            );

            // 3. Simula a chamada da IA baseada nas novas regras
            String respostaIA = simularRespostaComportamentalIA(diferenca, justificativa, ocorrenciasMes);

            // 4. Salva o veredito da IA no banco de dados
            caixa.setAnaliseAuditoriaIa(respostaIA);
            caixaRepository.save(caixa);

            log.info("Auditoria IA concluída com sucesso para o caixa #{}!", caixaId);

        } catch (Exception e) {
            log.error("Falha ao auditar caixa com IA: {}", e.getMessage(), e);
        }
    }

    // Mock Inteligente para simular o que o ChatGPT/Gemini faria com esse Prompt
    private String simularRespostaComportamentalIA(double diferenca, String justificativa, int ocorrenciasMes) {
        if (justificativa == null || justificativa.isBlank()) {
            return "[RISCO: ALTO] Nenhuma justificativa foi fornecida. Risco iminente de desvio financeiro não reportado.";
        }

        String justLowerCase = justificativa.toLowerCase();
        boolean desculpaComum = justLowerCase.contains("troco") || justLowerCase.contains("moeda") || justLowerCase.contains("engan");

        // A IA cruza a justificativa com o HISTÓRICO
        if (ocorrenciasMes >= 3) {
            return "[RISCO: ALTO] O operador apresenta PADRÃO RECORRENTE suspeito (" + ocorrenciasMes + " quebras em 30 dias). A justificativa de erro operacional perde a credibilidade. Risco altíssimo de fraude contínua ('formiguinha'). Intervenção e auditoria de câmeras altamente recomendada.";
        }

        if (Math.abs(diferenca) > 50.0) {
            return "[RISCO: MEDIO] O valor divergente é alto para ser classificado apenas como erro de troco. A justificativa requer validação humana pelo gerente da loja.";
        }

        if (desculpaComum) {
            return "[RISCO: BAIXO] Evento isolado no histórico recente do operador. A justificativa operacional é plausível e compatível com o baixo valor financeiro. Monitorar próximos turnos.";
        }

        return "[RISCO: MEDIO] Justificativa atípica. Requer validação inicial do gerente.";
    }
}