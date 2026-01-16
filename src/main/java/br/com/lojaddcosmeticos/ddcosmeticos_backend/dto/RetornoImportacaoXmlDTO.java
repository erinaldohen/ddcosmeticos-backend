package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusMatch;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class RetornoImportacaoXmlDTO {
    private Long fornecedorId;
    private String nomeFornecedor;
    private String numeroNota;
    private List<ItemXmlDTO> itensXml = new ArrayList<>();

    @Data
    public static class ItemXmlDTO {
        private String codigoBarras;
        private String descricao;
        private String ncm;      // <--- NOVO
        private String unidade;  // <--- NOVO
        private BigDecimal quantidade;
        private BigDecimal precoCusto;
        private BigDecimal total;
        private Long idProduto;
        private boolean novoProduto;
        private String codigoNoFornecedor; // Novo: para salvar o vínculo
        private StatusMatch statusMatch;   // Novo
        private String motivoMatch;        // Novo
        private String nomeProdutoSugerido;// Novo
        private boolean alertaDivergencia; // Novo
    }
}