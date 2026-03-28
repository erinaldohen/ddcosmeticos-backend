package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.InsightIA;
import org.springframework.data.domain.Pageable;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.InsightIARepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaPerdidaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MotorInteligenciaService {

    private final InsightIARepository insightIARepository;
    private final ProdutoRepository produtoRepository;
    private final VendaRepository vendaRepository;
    private final VendaPerdidaRepository perdidaRepository;

    // Variável em memória para lembrar se a IA já trabalhou hoje
    private LocalDate dataUltimaExecucao = null;

    // Roda 1 minuto (60000ms) após o sistema iniciar, e depois verifica a cada 1 hora (3600000ms)
    @Scheduled(initialDelay = 60000, fixedRate = 3600000)
    @Transactional
    public void processarInsightsDiarios() {
        LocalDate hoje = LocalDate.now();

        // Se já rodou hoje, sai silenciosamente sem gastar processamento
        if (hoje.equals(dataUltimaExecucao)) {
            return;
        }

        log.info("🧠 Acordando Motor de Inteligência Analítica (Verificação diária)...");

        // Limpa a base antiga de insights não resolvidos para gerar um cenário fresco
        insightIARepository.deleteAll();

        analisarValidadeRunRate();
        analisarRupturaCritica();
        analisarAnomaliasFinanceiras();

        // Regista que a tarefa de hoje está concluída
        dataUltimaExecucao = hoje;

        log.info("✅ Análise da IA concluída com sucesso. O motor voltará a rodar amanhã.");
    }

    private void analisarValidadeRunRate() {
        // Analisa produtos que vencem nos próximos 90 dias
        LocalDate limite = LocalDate.now().plusDays(90);
        List<Produto> produtos = produtoRepository.findAll().stream()
                .filter(p -> p.isAtivo() && p.getQuantidadeEmEstoque() > 0 && p.getValidade() != null && p.getValidade().isBefore(limite))
                .toList();

        for (Produto p : produtos) {
            long diasParaVencer = ChronoUnit.DAYS.between(LocalDate.now(), p.getValidade());
            if (diasParaVencer <= 0) continue; // Já venceu, requer outro tipo de tratamento

            BigDecimal vendaMedia = p.getVendaMediaDiaria() != null ? p.getVendaMediaDiaria() : BigDecimal.ZERO;

            if (vendaMedia.compareTo(BigDecimal.ZERO) > 0) {
                double diasEstoque = p.getQuantidadeEmEstoque() / vendaMedia.doubleValue();

                // Se o estoque vai durar mais dias do que a validade permite...
                if (diasEstoque > diasParaVencer) {
                    int sobraEstimada = (int) (p.getQuantidadeEmEstoque() - (vendaMedia.doubleValue() * diasParaVencer));
                    if (sobraEstimada > 0) {
                        salvarInsight("VALIDADE", "ALTA",
                                "Risco de Prejuízo por Validade: " + p.getDescricao(),
                                String.format("Faltam %d dias para vencer. A velocidade atual de vendas (%.1f/dia) é lenta para este lote. Estima-se que %d unidades sobrarão na prateleira estragadas.", diasParaVencer, vendaMedia.doubleValue(), sobraEstimada),
                                "Crie um Combo 'Leve 2 Pague 1' ou aplique 30% de desconto imediatamente na frente de caixa para recuperar o capital investido."
                        );
                    }
                }
            } else if (p.getQuantidadeEmEstoque() >= 5) {
                // Produto encalhado (não vende nada) e a vencer em breve
                salvarInsight("VALIDADE", "MEDIA",
                        "Estoque Encalhado a Vencer: " + p.getDescricao(),
                        String.format("Faltam %d dias para vencer, você tem %d unidades no estoque e NENHUMA venda consistente recente.", diasParaVencer, p.getQuantidadeEmEstoque()),
                        "Liquidação de emergência. Posicione o produto na ponta de gôndola ou na bancada do caixa a preço de custo."
                );
            }
        }
    }

    private void analisarRupturaCritica() {
        LocalDateTime seteDiasAtras = LocalDateTime.now().minusDays(7);
        List<Object[]> perdas = perdidaRepository.countVendasPerdidasAgrupadasDesde(seteDiasAtras);

        for (Object[] obj : perdas) {
            String nomeProduto = (String) obj[0];
            Long qtdPerdida = (Long) obj[1];

            if (qtdPerdida >= 3) {
                salvarInsight("RUPTURA", "ALTA",
                        "Ruptura Crítica com Perda de Vendas",
                        String.format("A loja perdeu %d vendas nos últimos 7 dias porque os clientes procuraram '%s' e não encontraram.", qtdPerdida, nomeProduto),
                        "Contacte o fornecedor urgente. Este item tem alta procura orgânica e está a causar perda direta de receita diária."
                );
            }
        }
    }

    private void analisarAnomaliasFinanceiras() {
        LocalDateTime ontemInicio = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime ontemFim = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);

        // 🚨 CORREÇÃO: Adicionado o Pageable.unpaged() e o .getContent()
        List<Venda> vendas = vendaRepository.findByDataVendaBetween(ontemInicio, ontemFim, Pageable.unpaged()).getContent();

        for (Venda v : vendas) {
            if (v.getDescontoTotal() != null && v.getDescontoTotal().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal valorRealTabela = v.getValorTotal().add(v.getDescontoTotal());
                BigDecimal percentualDesconto = v.getDescontoTotal().divide(valorRealTabela, 4, RoundingMode.HALF_UP);

                // Se a venda teve mais de 25% de desconto global na frente de caixa
                if (percentualDesconto.compareTo(new BigDecimal("0.25")) > 0) {
                    salvarInsight("FRAUDE", "MEDIA",
                            "Auditoria: Desconto Anormal no PDV",
                            String.format("A Venda Nº %d registou um desconto incomum de %s%% (R$ %s abatidos) pelo operador de caixa.", v.getIdVenda(), percentualDesconto.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP), v.getDescontoTotal()),
                            "Reveja a venda no menu Histórico e confirme com a equipa se este desconto profundo foi previamente autorizado pela gerência."
                    );
                }
            }
        }
    }

    private void salvarInsight(String tipo, String criticidade, String titulo, String msg, String acao) {
        InsightIA insight = new InsightIA(null, tipo, criticidade, titulo, msg, acao, LocalDateTime.now(), false);
        insightIARepository.save(insight);
    }
}