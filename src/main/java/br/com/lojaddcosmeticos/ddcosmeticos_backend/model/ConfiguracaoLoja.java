package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.AmbienteFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.LarguraPapel;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TemaSistema;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.infrastructure.converter.CryptoConverter;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
@Table(name = "configuracao_loja")
public class ConfiguracaoLoja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========================================================================
    // 1. DADOS DA EMPRESA
    // ========================================================================

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(name = "razao_social")
    private String razaoSocial;

    private String cnpj;
    private String email;
    private String telefone;

    // --- Endereço Granular ---
    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;

    @Column(length = 2)
    private String uf;

    // ========================================================================
    // 2. PARÂMETROS FISCAIS (NFC-e)
    // ========================================================================

    @Enumerated(EnumType.STRING) // Salva "HOMOLOGACAO" ou "PRODUCAO"
    @Column(name = "ambiente_fiscal", nullable = false)
    private AmbienteFiscal ambienteFiscal = AmbienteFiscal.HOMOLOGACAO;

    // --- Credenciais Homologação (CRIPTOGRAFADO) ---
    @Convert(converter = CryptoConverter.class)
    @Column(name = "h_token_nfce")
    private String homologacaoToken;

    @Column(name = "h_csc_id")
    private String homologacaoCscId;

    // --- Credenciais Produção (CRIPTOGRAFADO) ---
    @Convert(converter = CryptoConverter.class)
    @Column(name = "p_token_nfce")
    private String producaoToken;

    @Column(name = "p_csc_id")
    private String producaoCscId;

    // ========================================================================
    // 3. PARÂMETROS DO SISTEMA
    // ========================================================================

    @Column(name = "notificacoes_email")
    private Boolean notificacoesEmail = true;

    @Column(name = "backup_automatico")
    private Boolean backupAutomatico = false;

    @Column(name = "impressao_automatica")
    private Boolean impressaoAutomatica = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "largura_papel")
    private LarguraPapel larguraPapel = LarguraPapel.MM_80;

    @Enumerated(EnumType.STRING)
    @Column(name = "tema_sistema")
    private TemaSistema temaSistema = TemaSistema.LIGHT;

    // ========================================================================
    // 4. PARÂMETROS FINANCEIROS E PRECIFICAÇÃO
    // ========================================================================

    @Column(name = "margem_lucro_alvo")
    private BigDecimal margemLucroAlvo = new BigDecimal("30.00");

    @Column(name = "perc_impostos_venda")
    private BigDecimal percentualImpostosVenda = new BigDecimal("4.00");

    @Column(name = "perc_custo_fixo")
    private BigDecimal percentualCustoFixo = new BigDecimal("10.00");

    // ========================================================================
    // 5. PARÂMETROS DE SEGURANÇA (PDV)
    // ========================================================================

    @Column(name = "max_desconto_caixa")
    private BigDecimal percentualMaximoDescontoCaixa = new BigDecimal("5.00");

    @Column(name = "max_desconto_gerente")
    private BigDecimal percentualMaximoDescontoGerente = new BigDecimal("20.00");
}