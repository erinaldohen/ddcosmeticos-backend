package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.infrastructure.converter.CryptoConverter;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_configuracao")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class ConfiguracaoLoja implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    @ToString.Include
    private Long id;

    // ==================================================================================
    // 🚨 CAMPOS RAIZ (Mesmo nível do Flyway)
    // ==================================================================================

    // E-mail (SMTP)
    @Column(length = 100) private String smtpHost;
    private Integer smtpPort;
    @Column(length = 100) private String smtpUsername;
    @Column(length = 100) private String smtpPassword;

    // Gateways de Pagamento (InfinitePay, Mercado Pago, etc)
    @Column(length = 50) private String gatewayPagamento = "MANUAL";
    @Column(length = 255) private String infinitepayClientId;
    @Column(length = 255) private String infinitepayClientSecret;
    @Column(length = 255) private String infinitepayWalletId;

    // ==================================================================================
    // EMBEDDABLES (Abas de Configuração)
    // ==================================================================================
    @Embedded private DadosLoja loja = new DadosLoja();
    @Embedded private EnderecoLoja endereco = new EnderecoLoja();
    @Embedded private DadosFiscal fiscal = new DadosFiscal();
    @Embedded private DadosFinanceiro financeiro = new DadosFinanceiro();
    @Embedded private DadosVendas vendas = new DadosVendas();
    @Embedded private DadosSistema sistema = new DadosSistema();
    @Embedded private DadosComissoes comissoes = new DadosComissoes();

    public boolean isProducao() {
        return fiscal != null && "PRODUCAO".equalsIgnoreCase(fiscal.getAmbiente());
    }

    @PostLoad
    @PrePersist
    @PreUpdate
    public void garantirInstancias() {
        if (this.loja == null) this.loja = new DadosLoja();
        if (this.endereco == null) this.endereco = new EnderecoLoja();
        if (this.fiscal == null) this.fiscal = new DadosFiscal();
        if (this.financeiro == null) this.financeiro = new DadosFinanceiro();
        if (this.vendas == null) this.vendas = new DadosVendas();
        if (this.sistema == null) this.sistema = new DadosSistema();
        if (this.comissoes == null) this.comissoes = new DadosComissoes();
    }

    public String getCnpjLimpo() {
        return (loja != null && loja.getCnpj() != null) ? loja.getCnpj().replaceAll("\\D", "") : "";
    }

    // ==================================================================================
    // CLASSES INTERNAS (EMBEDDABLES)
    // ==================================================================================

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DadosLoja {
        @Column(length = 150) private String razaoSocial;
        @Column(length = 150) private String nomeFantasia;
        @Column(length = 18) private String cnpj;
        @Column(length = 50) private String ie;
        @Column(length = 50) private String im;
        @Column(length = 20) private String cnae;
        @Column(length = 150) private String email;
        @Column(length = 20) private String telefone;
        @Column(length = 20) private String whatsapp;
        @Column(length = 150) private String site;
        @Column(length = 100) private String instagram;
        @Column(length = 255) private String slogan;
        @Column(length = 20) private String corDestaque;
        private Boolean isMatriz = true;
        private LocalTime horarioAbre;
        private LocalTime horarioFecha;
        private Integer toleranciaMinutos = 0;
        private Boolean bloqueioForaHorario = false;
        @Column(precision = 15, scale = 2) private BigDecimal taxaEntregaPadrao = BigDecimal.ZERO;
        private Integer tempoEntregaMin = 30;
        @Column(columnDefinition = "TEXT") private String logoUrl;
    }

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class EnderecoLoja {
        @Column(length = 10) private String cep;
        @Column(length = 255) private String logradouro;
        @Column(length = 20) private String numero;
        @Column(length = 100) private String complemento;
        @Column(length = 100) private String bairro;
        @Column(length = 100) private String cidade;
        @Column(length = 2) private String uf;
    }

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DadosFiscal {
        @Column(length = 20) private String ambiente = "HOMOLOGACAO";
        @Column(length = 20) private String regime = "1";

        // Identificadores de Homologação
        @Column(length = 100) private String tokenHomologacao;
        @Column(length = 50) private String cscIdHomologacao;
        private Integer serieHomologacao = 1;
        private Integer nfeHomologacao = 1;

        // Identificadores de Produção
        @Column(length = 100) private String tokenProducao;
        @Column(length = 50) private String cscIdProducao;
        private Integer serieProducao = 1;
        private Integer nfeProducao = 1;

        // Certificado Digital
        private String caminhoCertificado;
        private byte[] arquivoCertificado;

        @Convert(converter = CryptoConverter.class)
        @Column(length = 500) private String senhaCert;

        @Column(length = 50) private String csrtId;

        @Convert(converter = CryptoConverter.class)
        @Column(length = 500) private String csrtHash;

        // Regras Fiscais Padrão
        private String ibptToken;
        private String naturezaPadrao = "VENDA DE MERCADORIA";
        private String emailContabil;
        private Boolean enviarXmlAutomatico = true;
        @Column(precision = 10, scale = 4) private BigDecimal aliquotaInterna = new BigDecimal("18.00");
        private Boolean modoContingencia = false;
        private Boolean priorizarMonofasico = true;

        // 🚨 DIRETIVA DO GESTOR: Valor padrão para NFC-e sem cadastro
        @Column(length = 500) private String obsPadraoCupom = "Consumidor Não Identificado";

        public String getCscIdAtivo() {
            String id = "PRODUCAO".equalsIgnoreCase(ambiente) ? cscIdProducao : cscIdHomologacao;
            return (id != null) ? id.replaceAll("^0+", "") : "1";
        }

        public String getTokenAtivo() {
            return "PRODUCAO".equalsIgnoreCase(ambiente) ? tokenProducao : tokenHomologacao;
        }
    }

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DadosFinanceiro {
        @Column(precision = 15, scale = 2) private BigDecimal comissaoProdutos = BigDecimal.ZERO;
        @Column(precision = 15, scale = 2) private BigDecimal comissaoServicos = BigDecimal.ZERO;
        @Column(precision = 15, scale = 2) private BigDecimal alertaSangria = new BigDecimal("500.00");
        @Column(precision = 15, scale = 2) private BigDecimal fundoTrocoPadrao = BigDecimal.ZERO;
        @Column(precision = 15, scale = 2) private BigDecimal metaDiaria = BigDecimal.ZERO;
        @Column(precision = 10, scale = 2) private BigDecimal taxaDebito = BigDecimal.ZERO;
        @Column(precision = 10, scale = 2) private BigDecimal taxaCredito = BigDecimal.ZERO;
        @Column(precision = 15, scale = 2) private BigDecimal descCaixa = new BigDecimal("5.00");
        @Column(precision = 15, scale = 2) private BigDecimal descGerente = new BigDecimal("20.00");
        private Boolean descExtraPix = false;
        private Boolean bloquearAbaixoCusto = true;
        @Column(length = 50) private String pixTipo;
        @Column(length = 255) private String pixChave;
        private Boolean aceitaDinheiro = true;
        private Boolean aceitaPix = true;
        private Boolean aceitaCredito = true;
        private Boolean aceitaDebito = true;
        private Boolean aceitaCrediario = false;
        @Column(precision = 10, scale = 2) private BigDecimal jurosMensal = BigDecimal.ZERO;
        @Column(precision = 10, scale = 2) private BigDecimal multaAtraso = BigDecimal.ZERO;
        private Integer diasCarencia = 0;
        private Boolean fechamentoCego = true;
    }

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DadosVendas {
        // 🚨 REGRA DE IDENTIFICAÇÃO DO PDV: Define o momento de solicitar CPF/CNPJ
        private String comportamentoCpf = "PERGUNTAR";
        private Boolean bloquearEstoque = true;
        private String layoutCupom;
        private Boolean imprimirVendedor;
        private Boolean imprimirTicketTroca;
        private Boolean autoEnterScanner;
        private Boolean fidelidadeAtiva;
        @Column(precision = 15, scale = 2) private BigDecimal pontosPorReal = BigDecimal.ONE;
        private Boolean usarBalanca;
        private Boolean agruparItens;
        @Column(precision = 15, scale = 2) private BigDecimal metaMensal = BigDecimal.ZERO;
    }

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DadosSistema {
        private Boolean impressaoAuto = true;
        @Column(length = 20) private String larguraPapel = "80MM";
        private Boolean backupAuto = true;
        private LocalTime backupHora = LocalTime.of(23, 0);
        @Column(length = 500) private String rodape;
        @Column(length = 50) private String tema = "DARK";
        private Boolean backupNuvem = false;
        private Boolean senhaGerenteCancelamento = true;
        @Column(length = 100) private String nomeTerminal = "CAIXA 01";
        private Boolean imprimirLogoCupom = true;
    }

    @Embeddable
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class DadosComissoes {
        @Column(length = 20) private String tipoCalculo = "GERAL";
        @Column(precision = 5, scale = 2) private BigDecimal percentualGeral = BigDecimal.ZERO;
        @Column(length = 20) private String comissionarSobre = "LUCRO";
        private Boolean descontarTaxasCartao = false;
    }
}