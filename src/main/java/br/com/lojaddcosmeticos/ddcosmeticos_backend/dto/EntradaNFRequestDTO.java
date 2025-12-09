// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/dto/EntradaNFRequestDTO.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO para registrar a entrada de uma Nota Fiscal (Compra) no estoque.
 */
@Data
public class EntradaNFRequestDTO {

    private String numeroNota;
    private String chaveAcesso;

    /**
     * ID do Operador (CAIXA01, GERENTE02, etc.) que está registrando a entrada.
     * Usado para auditoria.
     */
    private String matriculaOperador;

    private List<ItemEntradaDTO> itens;
}