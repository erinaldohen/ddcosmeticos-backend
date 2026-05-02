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
    // 🚨 CORREÇÃO: @Transactional REMOVIDO DAQUI!
    // Nunca manter uma transação de banco de dados aberta durante um Thread.sleep() ou chamada de rede demorada.
    public void auditarQuebraDeCaixa(Long caixaId, String nomeOperador, String justificativa) {
        log.info("Iniciando auditoria COMPORTAMENTAL IA em background para o caixa #{}", caixaId);

        try {
            // Dorme FORA da transação, sem prender o banco de dados
            Thread.sleep(2000);

            // Chama o método que abre a transação apenas para processar e salvar
            executarAnaliseESalvar(caixaId, nomeOperador, justificativa);

            log.info("Auditoria IA concluída com sucesso para o caixa #{}!", caixaId);
        } catch (Exception e) {
            log.error("Falha ao auditar caixa com IA: {}", e.getMessage(), e);
        }
    }

    @Transactional // 🚨 CORREÇÃO: Transação ultra-rápida, ativada apenas na hora de ler e gravar.
    public void executarAnaliseESalvar(Long caixaId, String nomeOperador, String justificativa) {
        CaixaDiario caixa = caixaRepository.findById(caixaId).orElseThrow();
        double diferenca = caixa.getDiferencaCaixa() != null ? caixa.getDiferencaCaixa().doubleValue() : 0.0;

        LocalDateTime trintaDiasAtras = LocalDateTime.now().minusDays(30);

        List<CaixaDiario> historicoOperador = caixaRepository.findAll().stream()
                .filter(c -> c.getUsuarioAbertura() != null && c.getUsuarioAbertura().getId().equals(caixa.getUsuarioAbertura().getId()))
                .filter(c -> c.getDataFechamento() != null && c.getDataFechamento().isAfter(trintaDiasAtras))
                .filter(c -> c.getDiferencaCaixa() != null && c.getDiferencaCaixa().compareTo(BigDecimal.ZERO) < 0)
                .toList();

        int ocorrenciasMes = historicoOperador.size();

        String respostaIA = simularRespostaComportamentalIA(diferenca, justificativa, ocorrenciasMes);

        caixa.setAnaliseAuditoriaIa(respostaIA);
        caixaRepository.save(caixa);
    }

    private String simularRespostaComportamentalIA(double diferenca, String justificativa, int ocorrenciasMes) {
        if (justificativa == null || justificativa.isBlank()) return "[RISCO: ALTO] Nenhuma justificativa foi fornecida.";
        String justLowerCase = justificativa.toLowerCase();
        boolean desculpaComum = justLowerCase.contains("troco") || justLowerCase.contains("moeda") || justLowerCase.contains("engan");

        if (ocorrenciasMes >= 3) return "[RISCO: ALTO] Padrão recorrente suspeito (" + ocorrenciasMes + " quebras em 30 dias).";
        if (Math.abs(diferenca) > 50.0) return "[RISCO: MEDIO] O valor divergente é alto.";
        if (desculpaComum) return "[RISCO: BAIXO] Evento isolado. Justificativa plausível.";
        return "[RISCO: MEDIO] Justificativa atípica.";
    }
}