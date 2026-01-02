package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.util.Date;

public record HistoricoProdutoDTO(
        Date dataHora,              // Convertemos o timestamp (long) para Date para facilitar no JSON
        String usuarioResponsavel,  // Quem fez a alteração
        BigDecimal precoVenda,      // O valor do preço naquela época
        Integer quantidadeEmEstoque,// O saldo de estoque naquela época
        String descricao            // O nome do produto naquela época
) {
    // Construtor canônico compacto (opcional, mas bom para validações se precisar)
}