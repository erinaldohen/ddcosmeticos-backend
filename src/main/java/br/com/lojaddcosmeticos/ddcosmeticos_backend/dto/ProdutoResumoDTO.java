package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;

// Este record contém APENAS o que a tela de listagem precisa.
// O precoCusto e dados de fornecedor ficam protegidos no Backend.
public record ProdutoResumoDTO(
        Long id,
        String descricao,
        String codigoBarras,
        BigDecimal precoVenda,
        Integer quantidadeEmEstoque,
        Integer estoqueMinimo,
        boolean ativo,
        String urlImagem
) {}