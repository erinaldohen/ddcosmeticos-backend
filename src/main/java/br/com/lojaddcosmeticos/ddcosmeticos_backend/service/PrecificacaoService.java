package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PrecificacaoService {

    @Autowired private ConfiguracaoLojaRepository configRepository;
    @Autowired private SugestaoPrecoRepository sugestaoRepository;
    @Autowired private ProdutoRepository produtoRepository;

    /**
     * Analisa se precisa sugerir novo preço após uma compra.
     */
    public void analisarImpactoCusto(Produto produto, BigDecimal novoCusto) {
        // 1. Carrega configurações globais (se não tiver, usa padrão seguro)
        ConfiguracaoLoja config = configRepository.findAll().stream().findFirst()
                .orElse(criarConfiguracaoPadrao());

        // 2. Calcula qual DEVERIA ser o preço para manter a margem alvo
        // Fórmula: Preço = Custo / (1 - (Impostos + CustosFixos + LucroAlvo))
        BigDecimal divisor = BigDecimal.ONE.subtract(
                (config.getPercentualImpostosVenda().add(
                        config.getPercentualCustoFixo()).add(
                        config.getMargemLucroAlvo()))
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
        );

        // Proteção contra divisão por zero
        if (divisor.compareTo(BigDecimal.ZERO) <= 0) divisor = new BigDecimal("0.1");

        BigDecimal precoIdeal = novoCusto.divide(divisor, 2, RoundingMode.HALF_UP);

        // 3. Verifica se o preço atual está muito defasado (Ex: diferença > 5 centavos)
        if (precoIdeal.compareTo(produto.getPrecoVenda()) > 0) {
            gerarSugestao(produto, novoCusto, precoIdeal, config);
        }
    }

    private void gerarSugestao(Produto produto, BigDecimal novoCusto, BigDecimal precoIdeal, ConfiguracaoLoja config) {
        // Evita duplicar sugestão pendente para o mesmo produto
        if (sugestaoRepository.existsByProdutoAndStatusPrecificacao(produto, StatusPrecificacao.PENDENTE)) {
            return;
        }

        SugestaoPreco sugestao = new SugestaoPreco();
        sugestao.setProduto(produto);
        sugestao.setCustoAntigo(produto.getPrecoCustoInicial()); // Ou PMP anterior
        sugestao.setCustoNovo(novoCusto);
        sugestao.setPrecoVendaAtual(produto.getPrecoVenda());
        sugestao.setPrecoVendaSugerido(precoIdeal);
        sugestao.setStatusPrecificacao(StatusPrecificacao.PENDENTE);

        // Calcula a margem atual (que caiu)
        BigDecimal receitaLiquidaAtual = produto.getPrecoVenda().multiply(
                BigDecimal.ONE.subtract(config.getPercentualImpostosVenda().divide(new BigDecimal(100)))
        );
        BigDecimal lucroBrutoAtual = receitaLiquidaAtual.subtract(novoCusto);
        // Margem = Lucro / Venda
        BigDecimal margemAtual = lucroBrutoAtual.divide(produto.getPrecoVenda(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100));

        sugestao.setMargemAtual(margemAtual);
        sugestao.setMargemProjetada(config.getMargemLucroAlvo());

        sugestao.setMotivo(String.format("Custo subiu para R$ %s. Margem caiu para %s%%.", novoCusto, margemAtual));

        sugestaoRepository.save(sugestao);
    }

    private ConfiguracaoLoja criarConfiguracaoPadrao() {
        ConfiguracaoLoja c = new ConfiguracaoLoja();
        c.setPercentualImpostosVenda(new BigDecimal("10")); // 10% Simples
        c.setPercentualCustoFixo(new BigDecimal("20"));     // 20% Operacional
        c.setMargemLucroAlvo(new BigDecimal("20"));         // 20% Lucro Líquido
        return c;
    }

    // Método para o Gerente APROVAR a sugestão
    public void aprovarSugestao(Long idSugestao) {
        SugestaoPreco sugestao = sugestaoRepository.findById(idSugestao)
                .orElseThrow(() -> new RuntimeException("Sugestão não encontrada"));

        Produto p = sugestao.getProduto();
        p.setPrecoVenda(sugestao.getPrecoVendaSugerido());
        produtoRepository.save(p);

        sugestao.setStatusPrecificacao(StatusPrecificacao.APROVADO);
        sugestaoRepository.save(sugestao);
    }

    public List<SugestaoPreco> listarSugestoesPendentes() {
        return sugestaoRepository.findByStatusPrecificacao(StatusPrecificacao.PENDENTE);
    }

    public void rejeitarSugestao(Long idSugestao) {
        SugestaoPreco sugestao = sugestaoRepository.findById(idSugestao)
                .orElseThrow(() -> new ResourceNotFoundException("Sugestão não encontrada"));

        sugestao.setStatusPrecificacao(StatusPrecificacao.REJEITADO);
        sugestaoRepository.save(sugestao);
    }

    /**
     * Aprovar com Preço Manual (Ex: Sistema sugeriu 121,43, mas gerente define 119,90)
     */
    public void aprovarComPrecoManual(Long idSugestao, BigDecimal precoManual) {
        SugestaoPreco sugestao = sugestaoRepository.findById(idSugestao)
                .orElseThrow(() -> new ResourceNotFoundException("Sugestão não encontrada"));

        Produto p = sugestao.getProduto();
        p.setPrecoVenda(precoManual); // Usa o preço que o gerente digitou
        produtoRepository.save(p);

        sugestao.setStatusPrecificacao(StatusPrecificacao.APROVADO);

        // Adiciona um registro no motivo para auditoria futura
        String observacao = String.format(" | Ajustado manualmente de %s (sugerido) para %s",
                sugestao.getPrecoVendaSugerido(), precoManual);

        // Concatena sem estourar o limite do banco (segurança)
        String novoMotivo = (sugestao.getMotivo() + observacao);
        if (novoMotivo.length() > 500) novoMotivo = novoMotivo.substring(0, 500);

        sugestao.setMotivo(novoMotivo);

        sugestaoRepository.save(sugestao);
    }
}