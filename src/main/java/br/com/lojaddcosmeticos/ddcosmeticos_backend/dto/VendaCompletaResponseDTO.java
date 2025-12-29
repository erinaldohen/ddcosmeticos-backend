package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VendaCompletaResponseDTO(
        Long id,
        LocalDateTime dataVenda,
        String clienteNome,
        String clienteDocumento, // <--- CAMPO NOVO (Ex: CPF ou CNPJ)
        BigDecimal totalVenda,
        BigDecimal descontoTotal,
        String statusFiscal,
        List<String> itensDescricao // Lista simples dos nomes dos produtos
) {}