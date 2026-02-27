package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.infrastructure.converter.CryptoConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_configuracao")
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // Regra de Ouro do JPA
@ToString(onlyExplicitlyIncluded = true)
public class ConfiguracaoLoja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
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
    // MÉTODOS UTILITÁRIOS PARA O SERVICE DE NFE
    // ==================================================================================

    public String getCnpjLimpo() {
        if (loja != null && loja.getCnpj() != null) {
            return loja.getCnpj().replaceAll("\\D", "");
        }
        return "00000000000000";
    }

    public String getCscIdAtivo() {
        if (fiscal == null) return "";
        return isProducao() ? fiscal.getCscIdProducao() : fiscal.getCscIdHomologacao();
    }

    public boolean isProducao() {
        return fiscal != null && "PRODUCAO".equalsIgnoreCase(fiscal.getAmbiente());
    }

    // ==================================================================================
    // CLASSES INTERNAS (EMBEDDABLES)
    // ==================================================================================

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosLoja {
        @Column(length = 150)
        private String razaoSocial;

        @Column(length = 150)
        private String nomeFantasia;

        @Column(length = 18)
        private String cnpj;

        @Column(length = 50)
        private String ie;

        @Column(length = 50)
        private String im;

        @Column(length = 20)
        private String cnae;

        @Column(length = 150)
        private String email;

        @Column(length = 20)
        private String telefone;

        @Column(length = 20)
        private String whatsapp;

        @Column(length = 150)
        private String site;

        @Column(length = 100)
        private String instagram;

        @Column(length = 255)
        private String slogan;

        @Column(length = 20)
        private String corDestaque;

        private Boolean isMatriz;

        @Column(columnDefinition = "TIME")
        private LocalTime horarioAbre;

        @Column(columnDefinition = "TIME")
        private LocalTime horarioFecha;

        private Integer toleranciaMinutos;
        private Boolean bloqueioForaHorario;

        @Column(precision = 15, scale = 2)
        private BigDecimal taxaEntregaPadrao;

        private Integer tempoEntregaMin;

        @Column(columnDefinition = "TEXT")
        private String logoUrl;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnderecoLoja {
        @Column(length = 10)
        private String cep;

        @Column(length = 255)
        private String logradouro;

        @Column(length = 20)
        private String numero;

        @Column(length = 100)
        private String complemento;

        @Column(length = 100)
        private String bairro;

        @Column(length = 100)
        private String cidade;

        @Column(length = 2)
        private String uf;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosFiscal {
        @Column(length = 20)
        private String ambiente; // "HOMOLOGACAO" ou "PRODUCAO"

        @Column(length = 20)
        private String regime;   // 1=Simples, 3=Normal

        // --- Configurações NFC-e (Homologação) ---
        @Column(length = 100)
        private String tokenHomologacao;

        @Column(length = 50)
        private String cscIdHomologacao;

        private Integer serieHomologacao;
        private Integer nfeHomologacao;

        // --- Configurações NFC-e (Produção) ---
        @Column(length = 100)
        private String tokenProducao;

        @Column(length = 50)
        private String cscIdProducao;

        private Integer serieProducao;
        private Integer nfeProducao;

        // --- Certificado Digital ---
        @Column(length = 255)
        private String caminhoCertificado;

        // DBA FIX: Removido @Lob. O PostgreSQL mapeará byte[] nativamente para bytea (Binary Array)
        // Isso melhora a performance e evita erros de OID no Postgres.
        private byte[] arquivoCertificado;

        @Convert(converter = CryptoConverter.class)
        @Column(length = 500) // Maior porque é criptografado
        private String senhaCert;

        // --- Compliance & Regras ---
        @Column(length = 50)
        private String csrtId;

        @Convert(converter = CryptoConverter.class)
        @Column(length = 500) // Maior porque é criptografado
        private String csrtHash;

        @Column(length = 255)
        private String ibptToken;

        @Column(length = 100)
        private String naturezaPadrao;

        @Column(length = 150)
        private String emailContabil;

        private Boolean enviarXmlAutomatico;

        @Column(precision = 10, scale = 4) // Escala de 4 para alíquotas é mais segura
        private BigDecimal aliquotaInterna;

        private Boolean modoContingencia;
        private Boolean priorizarMonofasico;

        @Column(length = 500)
        private String obsPadraoCupom;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosFinanceiro {
        // DBA: Todas as moedas e percentuais blindados com precisão
        @Column(precision = 15, scale = 2)
        private BigDecimal comissaoProdutos;

        @Column(precision = 15, scale = 2)
        private BigDecimal comissaoServicos;

        @Column(precision = 15, scale = 2)
        private BigDecimal alertaSangria;

        @Column(precision = 15, scale = 2)
        private BigDecimal fundoTrocoPadrao;

        @Column(precision = 15, scale = 2)
        private BigDecimal metaDiaria;

        // Taxas
        @Column(precision = 10, scale = 2)
        private BigDecimal taxaDebito;

        @Column(precision = 10, scale = 2)
        private BigDecimal taxaCredito;

        // Descontos
        @Column(precision = 15, scale = 2)
        private BigDecimal descCaixa;

        @Column(precision = 15, scale = 2)
        private BigDecimal descGerente;

        private Boolean descExtraPix;
        private Boolean bloquearAbaixoCusto;

        // Pix
        @Column(length = 50)
        private String pixTipo;

        @Column(length = 255)
        private String pixChave;

        // Formas Aceitas
        private Boolean aceitaDinheiro;
        private Boolean aceitaPix;
        private Boolean aceitaCredito;
        private Boolean aceitaDebito;
        private Boolean aceitaCrediario;

        // Crediário
        @Column(precision = 10, scale = 2)
        private BigDecimal jurosMensal;

        @Column(precision = 10, scale = 2)
        private BigDecimal multaAtraso;

        private Integer diasCarencia;

        private Boolean fechamentoCego;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosVendas {
        @Column(length = 50)
        private String comportamentoCpf;

        private Boolean bloquearEstoque;

        @Column(length = 50)
        private String layoutCupom;

        private Boolean imprimirVendedor;
        private Boolean imprimirTicketTroca;
        private Boolean autoEnterScanner;
        private Boolean fidelidadeAtiva;

        @Column(precision = 10, scale = 2)
        private BigDecimal pontosPorReal;

        private Boolean usarBalanca;
        private Boolean agruparItens;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosSistema {
        private Boolean impressaoAuto;

        @Column(length = 20)
        private String larguraPapel;

        private Boolean backupAuto;

        @Column(columnDefinition = "TIME")
        private LocalTime backupHora;

        @Column(length = 500)
        private String rodape;

        @Column(length = 50)
        private String tema;

        private Boolean backupNuvem;
        private Boolean senhaGerenteCancelamento;

        @Column(length = 100)
        private String nomeTerminal;

        private Boolean imprimirLogoCupom;
    }
}