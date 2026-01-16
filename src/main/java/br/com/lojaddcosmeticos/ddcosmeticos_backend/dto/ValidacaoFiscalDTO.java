package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

public class ValidacaoFiscalDTO {

    // O que o Frontend envia
    public record Request(
            String descricao,
            String ncm
    ) {}

    // O que o Backend devolve corrigido
    public record Response(
            String ncm,
            String cest,
            String cst,
            boolean monofasico,
            boolean impostoSeletivo,
            String origem // Opcional, geralmente 0
    ) {}
}