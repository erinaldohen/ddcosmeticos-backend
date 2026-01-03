package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.SugestaoPrecoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPrecificacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.SugestaoPreco;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.SugestaoPrecoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrecificacaoService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private SugestaoPrecoRepository sugestaoPrecoRepository;

    @Value("${app.margem-padrao-percentual:35}")
    private BigDecimal margemPadraoPercentual;

    @Value("${app.custo-fixo-percentual:9}")
    private BigDecimal custoFixoPercentual;

    @Value("${app.impostos-percentual:10}")
    private BigDecimal impostosPercentual;

    @Transactional
    public SugestaoPrecoDTO calcularSugestao(String codigoBarras) {
        Produto produto = produtoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        BigDecimal custoAtual = produto.getPrecoMedioPonderado();
        if (custoAtual == null || custoAtual.compareTo(BigDecimal.ZERO) <= 0) {
            custoAtual = produto.getPrecoCusto() != null ? produto.getPrecoCusto() : BigDecimal.ZERO;
            if (custoAtual.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Não é possível precificar produto sem custo definido.");
            }
        }

        BigDecimal totalPercentuais = margemPadraoPercentual.add(custoFixoPercentual).add(impostosPercentual);
        if (totalPercentuais.compareTo(new BigDecimal("99")) > 0) totalPercentuais = new BigDecimal("99");

        BigDecimal fatorDivisao = BigDecimal.ONE.subtract(totalPercentuais.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        BigDecimal precoSugerido = custoAtual.divide(fatorDivisao, 2, RoundingMode.HALF_UP);

        BigDecimal margemAtualPercentual = BigDecimal.ZERO;
        if (produto.getPrecoVenda() != null && produto.getPrecoVenda().compareTo(BigDecimal.ZERO) > 0) {
            margemAtualPercentual = produto.getPrecoVenda()
                    .subtract(custoAtual)
                    .divide(produto.getPrecoVenda(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        SugestaoPreco sugestao = sugestaoPrecoRepository.findByProdutoAndStatusPrecificacao(produto, StatusPrecificacao.PENDENTE)
                .orElse(new SugestaoPreco());

        sugestao.setProduto(produto);
        sugestao.setCustoBase(custoAtual);
        sugestao.setPrecoVendaAtual(produto.getPrecoVenda());
        sugestao.setPrecoVendaSugerido(precoSugerido);
        sugestao.setMargemAtualPercentual(margemAtualPercentual);
        sugestao.setMargemSugeridaPercentual(margemPadraoPercentual);
        sugestao.setDataSugestao(LocalDateTime.now());
        sugestao.setStatusPrecificacao(StatusPrecificacao.PENDENTE);
        sugestao.setObservacao("Cálculo automático (Mark-up).");

        sugestaoPrecoRepository.save(sugestao);
        return convertToDto(sugestao);
    }

    @Transactional
    public SugestaoPrecoDTO aprovarSugestao(Long sugestaoId) {
        SugestaoPreco sugestao = sugestaoPrecoRepository.findById(sugestaoId)
                .orElseThrow(() -> new RuntimeException("Sugestão não encontrada."));

        if (sugestao.getStatusPrecificacao() != StatusPrecificacao.PENDENTE) {
            throw new RuntimeException("Sugestão já processada.");
        }

        Produto produto = sugestao.getProduto();
        produto.setPrecoVenda(sugestao.getPrecoVendaSugerido());
        produtoRepository.save(produto);

        sugestao.setStatusPrecificacao(StatusPrecificacao.APROVADO);
        sugestao.setDataAprovacao(LocalDateTime.now());
        sugestaoPrecoRepository.save(sugestao);

        return convertToDto(sugestao);
    }

    // --- NOVO MÉTODO: Solicitado pelo Controller para aprovação manual ---
    @Transactional
    public void aprovarManual(Long sugestaoId, BigDecimal novoPreco) {
        SugestaoPreco sugestao = sugestaoPrecoRepository.findById(sugestaoId)
                .orElseThrow(() -> new RuntimeException("Sugestão não encontrada."));

        if (sugestao.getStatusPrecificacao() != StatusPrecificacao.PENDENTE) {
            throw new RuntimeException("Sugestão já processada.");
        }

        if (novoPreco == null || novoPreco.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("O novo preço deve ser maior que zero.");
        }

        Produto produto = sugestao.getProduto();
        produto.setPrecoVenda(novoPreco);
        produtoRepository.save(produto);

        sugestao.setPrecoVendaSugerido(novoPreco); // Atualiza o sugerido para refletir o que foi aceito
        sugestao.setStatusPrecificacao(StatusPrecificacao.APROVADO);
        sugestao.setDataAprovacao(LocalDateTime.now());
        sugestao.setObservacao("Aprovado com alteração manual pelo gerente.");
        sugestaoPrecoRepository.save(sugestao);
    }

    @Transactional
    public SugestaoPrecoDTO rejeitarSugestao(Long sugestaoId, String motivo) {
        SugestaoPreco sugestao = sugestaoPrecoRepository.findById(sugestaoId)
                .orElseThrow(() -> new RuntimeException("Sugestão não encontrada."));

        sugestao.setStatusPrecificacao(StatusPrecificacao.REJEITADO);
        sugestao.setDataAprovacao(LocalDateTime.now());
        sugestao.setObservacao("Rejeitado: " + motivo);
        sugestaoPrecoRepository.save(sugestao);

        return convertToDto(sugestao);
    }

    // --- CORREÇÃO: Método renomeado de 'buscar...' para 'listar...' para casar com o Controller ---
    @Transactional(readOnly = true)
    public List<SugestaoPrecoDTO> listarSugestoesPendentes() {
        return sugestaoPrecoRepository.findByStatusPrecificacao(StatusPrecificacao.PENDENTE).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private SugestaoPrecoDTO convertToDto(SugestaoPreco s) {
        return SugestaoPrecoDTO.builder()
                .id(s.getId())
                .produtoId(s.getProduto().getId())
                .nomeProduto(s.getProduto().getDescricao())
                .custoBase(s.getCustoBase())
                .precoVendaAtual(s.getPrecoVendaAtual())
                .precoVendaSugerido(s.getPrecoVendaSugerido())
                .margemAtualPercentual(s.getMargemAtualPercentual())
                .margemSugeridaPercentual(s.getMargemSugeridaPercentual())
                .dataSugestao(s.getDataSugestao())
                .status(s.getStatusPrecificacao())
                .observacao(s.getObservacao())
                .build();
    }
}