package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Map;

/**
 * DTO responsável por trafegar as configurações entre o Frontend (React) e o Backend (Spring).
 * Estrutura espelhada no objeto 'form' do Configuracoes.jsx.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoDTO {

    private Long id;

    // Seções principais (Abas do Frontend)
    private DadosLojaDTO loja;
    private EnderecoDTO endereco;
    private DadosFiscalDTO fiscal;
    private DadosFinanceiroDTO financeiro;
    private DadosVendasDTO vendas;
    private DadosSistemaDTO sistema;

    // --- CLASSES INTERNAS (Nested DTOs) ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosLojaDTO {
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

        private String corDestaque; // Ex: #ec4899
        private Boolean isMatriz;

        // Horários operacionais
        private LocalTime horarioAbre;
        private LocalTime horarioFecha;
        private Integer toleranciaMinutos;
        private Boolean bloqueioForaHorario;

        private String logoUrl; // URL para visualização (Upload é via endpoint separado)

        // Logística / Delivery
        private BigDecimal taxaEntregaPadrao;
        private Integer tempoEntregaMin;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnderecoDTO {
        private String cep;
        private String logradouro;
        private String numero;
        private String complemento;
        private String bairro;
        private String cidade;
        private String uf;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosFiscalDTO {
        private String ambiente; // "HOMOLOGACAO" ou "PRODUCAO"
        private String regime;   // "1", "2", "3"...

        // Objetos aninhados para separar credenciais
        private AmbienteFiscalDTO homologacao;
        private AmbienteFiscalDTO producao;

        // Certificado (Status ou Caminho)
        private String caminhoCertificado;
        private String senhaCert;

        // Compliance e Identificação
        private String csrtId;
        private String csrtHash;
        private String ibptToken;
        private String naturezaPadrao; // Ex: "5.102"
        private String emailContabil;
        private Boolean enviarXmlAutomatico;
        private BigDecimal aliquotaInterna;
        private Boolean modoContingencia;
        private Boolean priorizarMonofasico;
        private String obsPadraoCupom; // Texto legal para o rodapé da nota
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmbienteFiscalDTO {
        private String token;
        private String cscId;
        private Integer serie;
        private Integer nfe;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosFinanceiroDTO {
        // Comissões e Metas
        private BigDecimal comissaoProdutos;
        private BigDecimal comissaoServicos;
        private BigDecimal alertaSangria;
        private BigDecimal fundoTrocoPadrao;
        private BigDecimal metaDiaria;

        // Taxas Administrativas (Maquininha)
        private BigDecimal taxaDebito;
        private BigDecimal taxaCredito;

        // Regras de Desconto e Segurança
        private BigDecimal descCaixa;
        private BigDecimal descGerente;
        private Boolean descExtraPix;
        private Boolean bloquearAbaixoCusto;
        private Boolean fechamentoCego;

        // Pix
        private String pixTipo;
        private String pixChave;

        // Mapeamento de quais pagamentos estão ativos
        // Ex: { "dinheiro": true, "pix": true, "crediario": false }
        private Map<String, Boolean> pagamentos;

        // Regras do Crediário (Fiado)
        private BigDecimal jurosMensal;
        private BigDecimal multaAtraso;
        private Integer diasCarencia;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosVendasDTO {
        private String comportamentoCpf; // "PERGUNTAR", "SEMPRE", "NUNCA"
        private Boolean bloquearEstoque;
        private String layoutCupom;      // "DETALHADO", "RESUMIDO"
        private Boolean imprimirVendedor;
        private Boolean imprimirTicketTroca;
        private Boolean autoEnterScanner;
        private Boolean agruparItens;

        // Fidelidade e Hardware
        private Boolean fidelidadeAtiva;
        private BigDecimal pontosPorReal;
        private Boolean usarBalanca;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadosSistemaDTO {
        private Boolean impressaoAuto;
        private String larguraPapel; // "80mm"
        private Boolean backupAuto;
        private LocalTime backupHora;
        private String rodape;

        private String tema; // "light" ou "dark"
        private Boolean backupNuvem;
        private Boolean senhaGerenteCancelamento;
        private String nomeTerminal;
        private Boolean imprimirLogoCupom;
    }
}