package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor; // Adicionado para facilitar

import java.math.BigDecimal;

@Data
@NoArgsConstructor // Cria um construtor vazio caso precise
public class SugestaoCompraDTO {
    private String codigoBarras;
    private String descricao;
    private String marca;
    private Integer estoqueAtual;
    private Integer estoqueMinimo;
    private Integer quantidadeSugerida;

    // --- CAMPOS FALTANTES QUE CAUSAVAM O ERRO ---
    private String nivelUrgencia;
    private BigDecimal custoEstimado;

    // Construtor completo corrigido
    public SugestaoCompraDTO(String codigoBarras, String descricao, String marca,
                             Integer estoqueAtual, Integer estoqueMinimo,
                             Integer quantidadeSugerida, String nivelUrgencia,
                             BigDecimal custoEstimado) {
        this.codigoBarras = codigoBarras;
        this.descricao = descricao;
        this.marca = marca;
        this.estoqueAtual = estoqueAtual;
        this.estoqueMinimo = estoqueMinimo;
        this.quantidadeSugerida = quantidadeSugerida;
        this.nivelUrgencia = nivelUrgencia;
        this.custoEstimado = custoEstimado;
    }
}