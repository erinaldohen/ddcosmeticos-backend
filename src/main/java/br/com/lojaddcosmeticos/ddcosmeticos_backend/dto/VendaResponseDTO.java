package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class VendaResponseDTO implements Serializable { // <--- Implementar
    private static final long serialVersionUID = 1L;
    private Long idVenda;
    private LocalDateTime dataVenda;
    private Usuario usuario;
    private BigDecimal valorTotal;
    private BigDecimal desconto;
    private int totalItens;

    // Novo campo para avisar o caixa
    private List<String> alertas;

    // Status para saber se a nota foi gerada ou ficou pendente
    @Enumerated(EnumType.STRING)
    @Column(name = "status_fiscal", length = 11)
    private StatusFiscal statusFiscal;
}