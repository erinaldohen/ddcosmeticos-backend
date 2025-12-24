package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoPrecoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrecificacaoService {

    @Autowired
    private ConfiguracaoLojaRepository configRepo;

    @Autowired
    private ProdutoRepository produtoRepository;

    public SugestaoPrecoDTO calcularSugestao(Produto produto) {
        // ... (Mesma lógica de cálculo anterior) ...
        ConfiguracaoLoja config = configRepo.findById(1L).orElse(new ConfiguracaoLoja());

        BigDecimal custo = produto.getPrecoMedioPonderado();
        if (custo == null || custo.compareTo(BigDecimal.ZERO) == 0) custo = produto.getPrecoCusto();
        if (custo == null) custo = BigDecimal.ZERO;

        BigDecimal somaPercentuais = config.getPercentualImpostosVenda()
                .add(config.getPercentualCustoFixo())
                .add(config.getMargemLucroAlvo());

        BigDecimal divisor = BigDecimal.ONE.subtract(somaPercentuais.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP));
        if (divisor.compareTo(BigDecimal.ZERO) <= 0) divisor = new BigDecimal("0.1");

        BigDecimal precoSugerido = custo.divide(divisor, 2, RoundingMode.HALF_UP);

        BigDecimal margemAtual = BigDecimal.ZERO;
        String status = "SEM_PRECO";

        if (produto.getPrecoVenda() != null && produto.getPrecoVenda().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentualDespesas = config.getPercentualImpostosVenda().add(config.getPercentualCustoFixo());
            BigDecimal valorDespesas = produto.getPrecoVenda().multiply(percentualDespesas.divide(new BigDecimal(100)));
            BigDecimal lucroLiquido = produto.getPrecoVenda().subtract(custo).subtract(valorDespesas);

            margemAtual = lucroLiquido.divide(produto.getPrecoVenda(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100));

            if (margemAtual.compareTo(config.getMargemLucroAlvo()) >= 0) status = "LUCRO_BOM";
            else if (margemAtual.compareTo(BigDecimal.ZERO) > 0) status = "LUCRO_BAIXO";
            else status = "PREJUIZO";
        }

        // Importante: retornamos o ID do produto escondido no DTO se necessário,
        // ou o controller usa o ID da URL para achar o produto.
        return new SugestaoPrecoDTO(
                produto.getDescricao(),
                custo,
                produto.getPrecoVenda(),
                precoSugerido,
                margemAtual,
                config.getMargemLucroAlvo(),
                status
        );
    }

    public List<SugestaoPrecoDTO> listarSugestoesPendentes() {
        return produtoRepository.findAllByAtivoTrue().stream()
                .map(this::calcularSugestao)
                .filter(dto -> !dto.status().equals("LUCRO_BOM"))
                .collect(Collectors.toList());
    }

    // --- MÉTODOS DE AÇÃO (Correção dos erros do Controller) ---

    @Transactional
    public void aprovarSugestao(Long produtoId) {
        Produto p = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        // Recalcula para garantir que o valor está atualizado
        SugestaoPrecoDTO sugestao = calcularSugestao(p);

        // Aplica o preço sugerido no cadastro do produto
        p.setPrecoVenda(sugestao.precoSugerido());
        produtoRepository.save(p);
    }

    @Transactional
    public void aprovarComPrecoManual(Long produtoId, BigDecimal novoPreco) {
        Produto p = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        if (novoPreco == null || novoPreco.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Preço inválido");
        }

        p.setPrecoVenda(novoPreco);
        produtoRepository.save(p);
    }

    public void rejeitarSugestao(Long produtoId) {
        // Lógica de rejeição:
        // Como não temos tabela de histórico de sugestões, "Rejeitar" significa "Não fazer nada".
        // O preço antigo se mantém. O item continuará aparecendo na lista de alertas
        // até que o custo baixe ou o preço suba.

        // Futuramente, poderíamos adicionar um campo "data_ignorar_alerta_ate" no Produto.
    }
}