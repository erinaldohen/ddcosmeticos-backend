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
        // Busca configurações ou usa padrão vazio
        ConfiguracaoLoja config = configRepo.findById(1L).orElse(new ConfiguracaoLoja());

        // 1. Definição do Custo Base (Prioriza PMP, senão Custo Última Entrada)
        BigDecimal custo = produto.getPrecoMedioPonderado();
        if (custo == null || custo.compareTo(BigDecimal.ZERO) == 0) custo = produto.getPrecoCusto();
        if (custo == null) custo = BigDecimal.ZERO;

        // 2. Resgate seguro dos percentuais (Evita NullPointerException)
        BigDecimal impostos = config.getPercentualImpostosVenda() != null ? config.getPercentualImpostosVenda() : BigDecimal.ZERO;
        BigDecimal custoFixo = config.getPercentualCustoFixo() != null ? config.getPercentualCustoFixo() : BigDecimal.ZERO;
        BigDecimal margemAlvo = config.getMargemLucroAlvo() != null ? config.getMargemLucroAlvo() : BigDecimal.ZERO;

        // 3. Cálculo do Mark-up
        BigDecimal somaPercentuais = impostos.add(custoFixo).add(margemAlvo);

        // Fórmula de Mark-up divisor: 1 - (soma% / 100)
        // Ex: Se soma = 44%, divisor = 0.56. Preço = Custo / 0.56
        BigDecimal divisor = BigDecimal.ONE.subtract(somaPercentuais.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP));

        // Proteção contra divisão por zero ou negativa (se margem for > 100%)
        if (divisor.compareTo(BigDecimal.ZERO) <= 0) divisor = new BigDecimal("0.1");

        BigDecimal precoSugerido = custo.divide(divisor, 2, RoundingMode.HALF_UP);

        // 4. Análise da Situação Atual
        BigDecimal margemAtual = BigDecimal.ZERO;
        String status = "SEM_PRECO";

        if (produto.getPrecoVenda() != null && produto.getPrecoVenda().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal percentualDespesas = impostos.add(custoFixo);
            BigDecimal valorDespesas = produto.getPrecoVenda().multiply(percentualDespesas.divide(new BigDecimal(100), 4, RoundingMode.HALF_UP));

            BigDecimal lucroLiquido = produto.getPrecoVenda().subtract(custo).subtract(valorDespesas);

            margemAtual = lucroLiquido.divide(produto.getPrecoVenda(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal(100));

            // Define status
            if (margemAtual.compareTo(margemAlvo) >= 0) status = "LUCRO_BOM";
            else if (margemAtual.compareTo(BigDecimal.ZERO) > 0) status = "LUCRO_BAIXO";
            else status = "PREJUIZO";
        }

        return new SugestaoPrecoDTO(
                produto.getDescricao(),
                custo,
                produto.getPrecoVenda(),
                precoSugerido,
                margemAtual,
                margemAlvo,
                status
        );
    }

    public List<SugestaoPrecoDTO> listarSugestoesPendentes() {
        return produtoRepository.findAllByAtivoTrue().stream()
                .map(this::calcularSugestao)
                .filter(dto -> !dto.status().equals("LUCRO_BOM")) // Mostra tudo que não está ideal
                .collect(Collectors.toList());
    }

    // --- MÉTODOS DE AÇÃO ---

    @Transactional
    public void aprovarSugestao(Long produtoId) {
        Produto p = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        SugestaoPrecoDTO sugestao = calcularSugestao(p);

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
        // Apenas log ou lógica futura de "ignorar alerta por X dias"
    }
}