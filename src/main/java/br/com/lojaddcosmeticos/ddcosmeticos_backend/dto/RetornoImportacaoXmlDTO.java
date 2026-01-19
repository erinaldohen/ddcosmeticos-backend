package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusMatch;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RetornoImportacaoXmlDTO {

    private Long fornecedorId;
    private String nomeFornecedor;
    private String razaoSocialFornecedor; // Importante para o Front atualizar o dropdown
    private String numeroNota;

    // Inicializa a lista para evitar NullPointerException
    private List<ItemXmlDTO> itensXml = new ArrayList<>();

    // --- GETTERS E SETTERS DO PAI ---
    public Long getFornecedorId() { return fornecedorId; }
    public void setFornecedorId(Long fornecedorId) { this.fornecedorId = fornecedorId; }

    public String getNomeFornecedor() { return nomeFornecedor; }
    public void setNomeFornecedor(String nomeFornecedor) { this.nomeFornecedor = nomeFornecedor; }

    public String getRazaoSocialFornecedor() { return razaoSocialFornecedor; }
    public void setRazaoSocialFornecedor(String razaoSocialFornecedor) { this.razaoSocialFornecedor = razaoSocialFornecedor; }

    public String getNumeroNota() { return numeroNota; }
    public void setNumeroNota(String numeroNota) { this.numeroNota = numeroNota; }

    public List<ItemXmlDTO> getItensXml() { return itensXml; }
    public void setItensXml(List<ItemXmlDTO> itensXml) { this.itensXml = itensXml; }


    // --- CLASSE INTERNA (ITENS) ---
    public static class ItemXmlDTO {
        private Long idProduto; // Pode ser null
        private String codigoNoFornecedor;
        private String codigoBarras;
        private String descricao;
        private String ncm;
        private String unidade;
        private BigDecimal quantidade;
        private BigDecimal precoCusto;
        private BigDecimal total;

        // Campos de Inteligência (Essenciais para o novo Service)
        private StatusMatch statusMatch;
        private String motivoMatch;
        private String nomeProdutoSugerido;
        private boolean alertaDivergencia;
        private boolean novoProduto;

        // --- GETTERS E SETTERS DA CLASSE INTERNA ---
        public Long getIdProduto() { return idProduto; }
        public void setIdProduto(Long idProduto) { this.idProduto = idProduto; }

        public String getCodigoNoFornecedor() { return codigoNoFornecedor; }
        public void setCodigoNoFornecedor(String codigoNoFornecedor) { this.codigoNoFornecedor = codigoNoFornecedor; }

        public String getCodigoBarras() { return codigoBarras; }
        public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }

        public String getDescricao() { return descricao; }
        public void setDescricao(String descricao) { this.descricao = descricao; }

        public String getNcm() { return ncm; }
        public void setNcm(String ncm) { this.ncm = ncm; }

        public String getUnidade() { return unidade; }
        public void setUnidade(String unidade) { this.unidade = unidade; }

        public BigDecimal getQuantidade() { return quantidade; }
        public void setQuantidade(BigDecimal quantidade) { this.quantidade = quantidade; }

        public BigDecimal getPrecoCusto() { return precoCusto; }
        public void setPrecoCusto(BigDecimal precoCusto) { this.precoCusto = precoCusto; }

        public BigDecimal getTotal() { return total; }
        public void setTotal(BigDecimal total) { this.total = total; }

        public StatusMatch getStatusMatch() { return statusMatch; }
        public void setStatusMatch(StatusMatch statusMatch) { this.statusMatch = statusMatch; }

        public String getMotivoMatch() { return motivoMatch; }
        public void setMotivoMatch(String motivoMatch) { this.motivoMatch = motivoMatch; }

        public String getNomeProdutoSugerido() { return nomeProdutoSugerido; }
        public void setNomeProdutoSugerido(String nomeProdutoSugerido) { this.nomeProdutoSugerido = nomeProdutoSugerido; }

        public boolean isAlertaDivergencia() { return alertaDivergencia; }
        public void setAlertaDivergencia(boolean alertaDivergencia) { this.alertaDivergencia = alertaDivergencia; }

        public boolean isNovoProduto() { return novoProduto; }
        public void setNovoProduto(boolean novoProduto) { this.novoProduto = novoProduto; }
    }
}