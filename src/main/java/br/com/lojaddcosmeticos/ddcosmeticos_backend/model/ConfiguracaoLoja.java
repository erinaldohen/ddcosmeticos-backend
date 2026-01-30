package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_configuracao")
public class ConfiguracaoLoja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    private DadosLoja loja;

    @Embedded
    private EnderecoLoja endereco;

    @Embedded
    private DadosFiscal fiscal;

    @Embedded
    private DadosFinanceiro financeiro;

    @Embedded
    private DadosVendas vendas;

    @Embedded
    private DadosSistema sistema;

    // Inicializa objetos para evitar NullPointerException
    @PrePersist
    @PreUpdate
    public void preencherNulos() {
        if (loja == null) loja = new DadosLoja();
        if (endereco == null) endereco = new EnderecoLoja();
        if (fiscal == null) fiscal = new DadosFiscal();
        if (financeiro == null) financeiro = new DadosFinanceiro();
        if (vendas == null) vendas = new DadosVendas();
        if (sistema == null) sistema = new DadosSistema();
    }

    // ==================================================================================
    // CLASSES INTERNAS (EMBEDDABLES)
    // ==================================================================================

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosLoja {
        private String razaoSocial;
        private String nomeFantasia;
        private String cnpj;
        private String ie;
        private String im;
        private String cnae;
        private String email;
        private String telefone;
        private String whatsapp;
        private String site;
        private String instagram;
        private String slogan;
        private String corDestaque;
        private Boolean isMatriz;

        @Column(columnDefinition = "TIME")
        private LocalTime horarioAbre;

        @Column(columnDefinition = "TIME")
        private LocalTime horarioFecha;

        private Integer toleranciaMinutos;
        private Boolean bloqueioForaHorario;
        private BigDecimal taxaEntregaPadrao;
        private Integer tempoEntregaMin;
        private String logoUrl;
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnderecoLoja {
        private String cep;
        private String logradouro;
        private String numero;
        private String complemento;
        private String bairro;
        private String cidade;
        private String uf;
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosFiscal {
        private String ambiente; // "HOMOLOGACAO" ou "PRODUCAO"
        private String regime;   // 1=Simples, 3=Normal

        // --- Configurações NFC-e (Homologação) ---
        private String tokenHomologacao;
        private String cscIdHomologacao;
        private Integer serieHomologacao;
        private Integer nfeHomologacao;

        // --- Configurações NFC-e (Produção) ---
        private String tokenProducao;
        private String cscIdProducao;
        private Integer serieProducao;
        private Integer nfeProducao;

        // --- Certificado Digital ---
        private String caminhoCertificado;
        private String senhaCert;

        // --- Compliance & Regras ---
        private String csrtId;
        private String csrtHash;
        private String ibptToken;
        private String naturezaPadrao;
        private String emailContabil;
        private Boolean enviarXmlAutomatico;
        private BigDecimal aliquotaInterna;
        private Boolean modoContingencia;
        private Boolean priorizarMonofasico;

        @Column(length = 500)
        private String obsPadraoCupom;
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosFinanceiro {
        private BigDecimal comissaoProdutos;
        private BigDecimal comissaoServicos;
        private BigDecimal alertaSangria;
        private BigDecimal fundoTrocoPadrao;
        private BigDecimal metaDiaria;

        // Taxas
        private BigDecimal taxaDebito;
        private BigDecimal taxaCredito;

        // Descontos
        private BigDecimal descCaixa;
        private BigDecimal descGerente;
        private Boolean descExtraPix;
        private Boolean bloquearAbaixoCusto;

        // Pix
        private String pixTipo;
        private String pixChave;

        // Formas Aceitas
        private Boolean aceitaDinheiro;
        private Boolean aceitaPix;
        private Boolean aceitaCredito;
        private Boolean aceitaDebito;
        private Boolean aceitaCrediario;

        // Crediário
        private BigDecimal jurosMensal;
        private BigDecimal multaAtraso;
        private Integer diasCarencia;

        private Boolean fechamentoCego;
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosVendas {
        private String comportamentoCpf;
        private Boolean bloquearEstoque;
        private String layoutCupom;
        private Boolean imprimirVendedor;
        private Boolean imprimirTicketTroca;
        private Boolean autoEnterScanner;
        private Boolean fidelidadeAtiva;
        private BigDecimal pontosPorReal;
        private Boolean usarBalanca;
        private Boolean agruparItens;
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosSistema {
        private Boolean impressaoAuto;
        private String larguraPapel;
        private Boolean backupAuto;

        @Column(columnDefinition = "TIME")
        private LocalTime backupHora;

        private String rodape;
        private String tema;
        private Boolean backupNuvem;
        private Boolean senhaGerenteCancelamento;
        private String nomeTerminal;
        private Boolean imprimirLogoCupom;
    }
}