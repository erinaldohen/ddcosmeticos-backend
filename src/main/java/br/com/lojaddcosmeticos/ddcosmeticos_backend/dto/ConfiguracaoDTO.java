package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.br.CNPJ;

import java.math.BigDecimal;
import java.time.LocalTime;

public record ConfiguracaoDTO(
        Long id,
        LojaDTO loja,
        EnderecoDTO endereco,
        FiscalDTO fiscal,
        FinanceiroDTO financeiro,
        VendasDTO vendas,
        SistemaDTO sistema
) {
    public record LojaDTO(
            @NotBlank String razaoSocial, String nomeFantasia, @CNPJ String cnpj, String ie, String im, String cnae,
            String email, String telefone, String whatsapp, String site, String instagram, String slogan,
            String corDestaque, Boolean isMatriz, LocalTime horarioAbre, LocalTime horarioFecha,
            Integer toleranciaMinutos, Boolean bloqueioForaHorario, BigDecimal taxaEntregaPadrao,
            Integer tempoEntregaMin, String logoUrl
    ) {}

    public record EnderecoDTO(
            String cep, String logradouro, String numero, String complemento, String bairro, String cidade, String uf
    ) {}

    public record FiscalDTO(
            String ambiente, String regime,
            FiscalAmbienteDTO homologacao, // Agrupado no front, desagrupado no banco
            FiscalAmbienteDTO producao,    // Agrupado no front, desagrupado no banco
            String caminhoCertificado, String senhaCert,
            String csrtId, String csrtHash, String ibptToken, String naturezaPadrao,
            String emailContabil, Boolean enviarXmlAutomatico, BigDecimal aliquotaInterna,
            Boolean modoContingencia, Boolean priorizarMonofasico, String obsPadraoCupom
    ) {}

    public record FiscalAmbienteDTO(String token, String cscId, Integer serie, Integer nfe) {}

    public record FinanceiroDTO(
            BigDecimal comissaoProdutos, BigDecimal comissaoServicos, BigDecimal alertaSangria,
            BigDecimal fundoTrocoPadrao, BigDecimal metaDiaria, BigDecimal taxaDebito, BigDecimal taxaCredito,
            BigDecimal descCaixa, BigDecimal descGerente, Boolean descExtraPix, Boolean bloquearAbaixoCusto,
            String pixTipo, String pixChave, PagamentosDTO pagamentos,
            BigDecimal jurosMensal, BigDecimal multaAtraso, Integer diasCarencia, Boolean fechamentoCego
    ) {}

    public record PagamentosDTO(Boolean dinheiro, Boolean pix, Boolean credito, Boolean debito, Boolean crediario) {}

    public record VendasDTO(
            String comportamentoCpf, Boolean bloquearEstoque, String layoutCupom, Boolean imprimirVendedor,
            Boolean imprimirTicketTroca, Boolean autoEnterScanner, Boolean fidelidadeAtiva, BigDecimal pontosPorReal,
            Boolean usarBalanca, Boolean agruparItens
    ) {}

    public record SistemaDTO(
            Boolean impressaoAuto, String larguraPapel, Boolean backupAuto, LocalTime backupHora,
            String rodape, String tema, Boolean backupNuvem, Boolean senhaGerenteCancelamento,
            String nomeTerminal, Boolean imprimirLogoCupom
    ) {}
}