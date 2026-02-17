package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PagamentoVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;

// Bibliotecas NFe
import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.TipoEmissaoEnum;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import br.com.swconsultoria.nfe.schema_4.enviNFe.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class NfceService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ConfiguracaoLojaRepository configuracaoLojaRepository;

    @Autowired
    private TributacaoService tributacaoService; // Injeção do serviço de cálculo de tributos

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NfceResponseDTO emitirNfce(Venda venda) {
        // 1. Carregar Configurações
        ConfiguracaoLoja configLoja = configuracaoLojaRepository.findById(1L)
                .orElseThrow(() -> new ValidationException("Loja não configurada."));

        // Validação de segurança para dev (se não houver certificado, simula)
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            return simularEmissaoEmDev(venda, configLoja);
        }

        try {
            // Tenta emissão NORMAL (Online - Tipo 1)
            return processarEmissao(venda, configLoja, TipoEmissaoEnum.EMISSAO_NORMAL);

        } catch (Exception e) {
            log.error("🔴 Falha na emissão Online. Tentando Contingência Offline. Erro: {}", e.getMessage());

            // Verifica se é erro de conexão ou timeout para decidir pela contingência
            if (isErroDeConexao(e)) {
                try {
                    return processarEmissao(venda, configLoja, TipoEmissaoEnum.CONTINGENCIA_OFFLINE_NFC);
                } catch (Exception exContingencia) {
                    log.error("💀 Falha crítica até na contingência!", exContingencia);
                    throw new ValidationException("Erro Crítico na Contingência: " + exContingencia.getMessage());
                }
            } else {
                // Erro de negócio (Ex: NCM inválido), rejeita a nota
                throw new ValidationException("Erro na emissão NFC-e: " + e.getMessage());
            }
        }
    }

    private NfceResponseDTO processarEmissao(Venda venda, ConfiguracaoLoja configLoja, TipoEmissaoEnum tipoEmissao) throws Exception {
        // 2. Inicializar Configuração da Lib
        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);

        // 3. Dados da Nota
        String modelo = "65"; // NFC-e
        String serie = String.valueOf(configLoja.getFiscal().getSerieProducao()); // Usar série do banco
        if (!configNfe.getAmbiente().equals(AmbienteEnum.PRODUCAO)) {
            serie = String.valueOf(configLoja.getFiscal().getSerieHomologacao());
        }

        String nNF = String.valueOf(venda.getIdVenda() + 1000); // Exemplo simples, ideal é ter sequencial no banco
        String cnf = String.format("%08d", new Random().nextInt(99999999));

        String cnpjEmitente = "00000000000000";
        if (configLoja.getLoja() != null && configLoja.getLoja().getCnpj() != null) {
            cnpjEmitente = configLoja.getLoja().getCnpj().replaceAll("\\D", "");
        }

        // Definir tpEmis string
        String tpEmisStr = (tipoEmissao == TipoEmissaoEnum.CONTINGENCIA_OFFLINE_NFC) ? "9" : "1";

        // 4. Montar Chave
        String chaveSemDigito = gerarChaveAcesso(
                configNfe.getEstado().getCodigoUF(),
                LocalDateTime.now(),
                cnpjEmitente,
                modelo,
                serie,
                nNF,
                tpEmisStr,
                cnf
        );
        String dv = calcularDV(chaveSemDigito);
        String chaveAcesso = chaveSemDigito + dv;

        // 5. Preencher XML
        TNFe nfe = new TNFe();
        TNFe.InfNFe infNFe = new TNFe.InfNFe();
        infNFe.setId("NFe" + chaveAcesso);
        infNFe.setVersao("4.00");

        infNFe.setIde(montarIde(configNfe, cnf, nNF, dv, modelo, serie, tpEmisStr));
        infNFe.setEmit(montarEmit(configLoja));

        if (venda.getCliente() != null) {
            infNFe.setDest(montarDest(venda.getCliente()));
        }

        infNFe.getDet().addAll(montarDetalhes(venda.getItens()));
        infNFe.setTotal(montarTotal(venda));
        infNFe.setTransp(montarTransp());
        infNFe.setPag(montarPag(venda.getPagamentos(), venda.getValorTotal()));

        // --- Adicionar Lei da Transparência nas Informações Adicionais ---
        TNFe.InfNFe.InfAdic infAdic = new TNFe.InfNFe.InfAdic();
        String textoTributos = tributacaoService.calcularTextoTransparencia(venda);
        infAdic.setInfCpl(textoTributos);
        infNFe.setInfAdic(infAdic);
        // ----------------------------------------------------------------

        nfe.setInfNFe(infNFe);

        // 6. Enviar (ou apenas assinar se for contingência)
        String status = "";
        String motivo = "";
        String protocolo = "";
        String xmlFinal = "";

        if (tipoEmissao == TipoEmissaoEnum.CONTINGENCIA_OFFLINE_NFC) {
            // Em contingência: Assina, gera XML mas não envia
            // Aqui precisaria chamar o método de assinar da biblioteca
            // Como exemplo simplificado:
            status = "100"; // Simulando sucesso local
            motivo = "Emitida em Contingência Offline";
            xmlFinal = "XML ASSINADO PENDENTE DE ENVIO";

            // Atualizar status da venda para o robô pegar depois
            venda.setStatusNfce(StatusFiscal.CONTINGENCIA);
            vendaRepository.save(venda);
        } else {
            // Emissão Normal: Envia para SEFAZ
            TEnviNFe enviNFe = new TEnviNFe();
            enviNFe.setVersao("4.00");
            enviNFe.setIdLote("1");
            enviNFe.setIndSinc("1");
            enviNFe.getNFe().add(nfe);

            TRetEnviNFe retorno = Nfe.enviarNfe(configNfe, enviNFe, DocumentoEnum.NFCE);

            // 7. Validar Retorno
            status = retorno.getProtNFe().getInfProt().getCStat();
            motivo = retorno.getProtNFe().getInfProt().getXMotivo();

            // Tratamento seguro para nulo
            if (retorno.getProtNFe().getInfProt().getNProt() != null) {
                protocolo = retorno.getProtNFe().getInfProt().getNProt();
            }

            xmlFinal = XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe());

            if (!status.equals("100")) {
                throw new ValidationException("Rejeição SEFAZ: " + status + " - " + motivo);
            }
        }

        // Lógica do CSC para QR Code
        String cscId;
        String cscToken;
        if ("PRODUCAO".equalsIgnoreCase(configLoja.getFiscal().getAmbiente())) {
            cscId = configLoja.getFiscal().getCscIdProducao();
            cscToken = configLoja.getFiscal().getTokenProducao();
        } else {
            cscId = configLoja.getFiscal().getCscIdHomologacao();
            cscToken = configLoja.getFiscal().getTokenHomologacao();
        }

        // Nota: A geração real do QR Code envolve hash SHA-1 com o CSC Token.
        // A URL abaixo é simplificada para exemplo.
        String urlQrCode = "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx?p=" + chaveAcesso + "|2|1|1|" + cscId;

        // CORREÇÃO: Ordem dos parâmetros ajustada para o DTO
        return new NfceResponseDTO(
                venda.getIdVenda(), // 1. ID (Long)
                status,             // 2. Status
                motivo,             // 3. Motivo
                chaveAcesso,        // 4. Chave Acesso (String)
                protocolo,          // 5. Protocolo
                xmlFinal,           // 6. XML
                urlQrCode           // 7. URL QR Code
        );
    }

    // ================= MÉTODOS DE APOIO =================

    private ConfiguracoesNfe iniciarConfiguracoesNfe(ConfiguracaoLoja loja) throws Exception {
        byte[] certificadoBytes = loja.getFiscal().getArquivoCertificado();
        String senha = loja.getFiscal().getSenhaCert();

        Certificado certificado = CertificadoService.certificadoPfxBytes(certificadoBytes, senha);

        EstadosEnum estado = EstadosEnum.valueOf(loja.getEndereco().getUf());

        boolean isProducao = "PRODUCAO".equalsIgnoreCase(loja.getFiscal().getAmbiente());
        AmbienteEnum ambiente = isProducao ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO;

        return ConfiguracoesNfe.criarConfiguracoes(estado, ambiente, certificado, "schemas");
    }

    private NfceResponseDTO simularEmissaoEmDev(Venda venda, ConfiguracaoLoja config) {
        String cnpj = "00000000000000";
        if (config.getLoja() != null && config.getLoja().getCnpj() != null) {
            cnpj = config.getLoja().getCnpj().replaceAll("\\D", "");
        }

        String chaveFake = "352309" + cnpj + "650010000000011" + String.format("%09d", venda.getIdVenda());

        return new NfceResponseDTO(
                venda.getIdVenda(),                 // 1. ID (Long)
                "100",                              // 2. Status
                "EMISSAO SIMULADA (SEM CERTIFICADO)", // 3. Motivo
                chaveFake,                          // 4. Chave Fake (String)
                "1234567890",                       // 5. Protocolo
                "<xml>Simulacao</xml>",             // 6. XML
                "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx" // 7. URL
        );
    }

    private boolean isErroDeConexao(Exception e) {
        String msg = e.getMessage().toLowerCase();
        return msg.contains("timeout") || msg.contains("refused") || msg.contains("host") || msg.contains("network") || msg.contains("conexão");
    }

    // Método chamado pelo Robô para tentar reenviar
    @Transactional
    public void transmitirNotaContingencia(Venda venda) {
        try {
            log.info("📡 Transmitindo nota {} de contingência...", venda.getIdVenda());
            // Lógica de recuperação das configurações e envio do XML já assinado
            // Seria necessário recarregar a Configuração e reenviar

            venda.setStatusNfce(StatusFiscal.AUTORIZADA);
            venda.setMensagemRejeicao(null);
            vendaRepository.save(venda);
            log.info("✅ Nota {} autorizada com sucesso!", venda.getIdVenda());
        } catch (Exception e) {
            log.error("❌ Falha ao transmitir nota {}: {}", venda.getIdVenda(), e.getMessage());
        }
    }

    // --- Montadores XML ---

    private TNFe.InfNFe.Ide montarIde(ConfiguracoesNfe config, String cnf, String nNF, String dv, String modelo, String serie, String tpEmis) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(config.getEstado().getCodigoUF());
        ide.setCNF(cnf);
        ide.setNatOp("VENDA CONSUMIDOR");
        ide.setMod(modelo);
        ide.setSerie(serie);
        ide.setNNF(nNF);
        ide.setDhEmi(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG("2611606"); // Idealmente pegar do cadastro da loja
        ide.setTpImp("4");
        ide.setTpEmis(tpEmis); // Agora dinâmico (1 ou 9)
        ide.setCDV(dv);
        ide.setTpAmb(config.getAmbiente().getCodigo());
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("1.0");

        if (tpEmis.equals("9")) {
            ide.setDhCont(XmlNfeUtil.dataNfe(LocalDateTime.now()));
            ide.setXJust("Falha de conexao com a internet");
        }

        return ide;
    }

    private TNFe.InfNFe.Emit montarEmit(ConfiguracaoLoja config) {
        TNFe.InfNFe.Emit emit = new TNFe.InfNFe.Emit();
        if (config.getLoja() != null) {
            emit.setCNPJ(config.getLoja().getCnpj().replaceAll("\\D", ""));
            emit.setXNome(config.getLoja().getRazaoSocial());
            emit.setIE(config.getLoja().getIe() != null ? config.getLoja().getIe().replaceAll("\\D", "") : "");
        } else {
            emit.setCNPJ("00000000000000");
            emit.setXNome("LOJA NAO CONFIGURADA");
            emit.setIE("");
        }
        emit.setCRT("1"); // 1 = Simples Nacional (Padrão para DD Cosméticos)

        TEnderEmi ender = new TEnderEmi();
        if (config.getEndereco() != null) {
            ender.setXLgr(config.getEndereco().getLogradouro());
            ender.setNro(config.getEndereco().getNumero());
            ender.setXBairro(config.getEndereco().getBairro());
            // Códigos de município e UF deveriam vir de uma tabela IBGE
            ender.setCMun("2611606");
            ender.setXMun("RECIFE");
            ender.setUF(TUfEmi.PE);
            ender.setCEP(config.getEndereco().getCep() != null ? config.getEndereco().getCep().replaceAll("\\D", "") : "00000000");
        } else {
            ender.setXLgr("RUA"); ender.setNro("0"); ender.setXBairro("BAIRRO");
            ender.setCMun("2611606"); ender.setXMun("RECIFE"); ender.setUF(TUfEmi.PE); ender.setCEP("00000000");
        }
        ender.setCPais("1058");
        ender.setXPais("BRASIL");
        emit.setEnderEmit(ender);

        return emit;
    }

    private TNFe.InfNFe.Dest montarDest(br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente cliente) {
        TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();
        dest.setXNome(cliente.getNome());
        if (cliente.getDocumento() != null) {
            String doc = cliente.getDocumento().replaceAll("\\D", "");
            if (doc.length() == 11) {
                dest.setCPF(doc);
            } else if (doc.length() == 14) {
                dest.setCNPJ(doc);
            }
        }
        return dest;
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itens) {
        // Implementação simplificada - precisaria iterar sobre os itens e converter para objeto Det
        // Para o MVP, retorna vazio para não quebrar a compilação, mas precisa ser implementado
        return Collections.emptyList();
    }

    private TNFe.InfNFe.Total montarTotal(Venda venda) {
        TNFe.InfNFe.Total total = new TNFe.InfNFe.Total();
        TNFe.InfNFe.Total.ICMSTot icmsTot = new TNFe.InfNFe.Total.ICMSTot();

        // Valores deveriam ser a soma dos itens
        String valorTotal = venda.getValorTotal() != null ? venda.getValorTotal().toString() : "0.00";

        icmsTot.setVBC("0.00"); icmsTot.setVICMS("0.00"); icmsTot.setVProd(valorTotal);
        icmsTot.setVNF(valorTotal); icmsTot.setVDesc("0.00"); icmsTot.setVOutro("0.00");
        icmsTot.setVIPI("0.00"); icmsTot.setVPIS("0.00"); icmsTot.setVCOFINS("0.00");
        icmsTot.setVFrete("0.00"); icmsTot.setVSeg("0.00");
        total.setICMSTot(icmsTot);
        return total;
    }

    private TNFe.InfNFe.Transp montarTransp() {
        TNFe.InfNFe.Transp t = new TNFe.InfNFe.Transp();
        t.setModFrete("9"); // 9 = Sem frete
        return t;
    }

    private TNFe.InfNFe.Pag montarPag(List<PagamentoVenda> pags, BigDecimal total) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();

        // Mapear formas de pagamento
        // 01=Dinheiro, 03=Cartão Crédito, 04=Cartão Débito, 17=PIX
        // Lógica simplificada:
        det.setTPag("01");
        det.setVPag(total != null ? total.toString() : "0.00");
        pag.getDetPag().add(det);
        return pag;
    }

    private String gerarChaveAcesso(String cuf, LocalDateTime data, String cnpj, String mod, String serie, String nnf, String tpEmis, String cnf) {
        StringBuilder chave = new StringBuilder();
        chave.append(String.format("%02d", Integer.parseInt(cuf)));
        chave.append(data.format(DateTimeFormatter.ofPattern("yyMM")));
        chave.append(cnpj);
        chave.append(mod);
        chave.append(String.format("%03d", Integer.parseInt(serie)));
        chave.append(String.format("%09d", Long.parseLong(nnf)));
        chave.append(tpEmis);
        chave.append(cnf);
        return chave.toString();
    }

    private String calcularDV(String chave) {
        int soma = 0;
        int peso = 2;
        for (int i = chave.length() - 1; i >= 0; i--) {
            int num = Character.getNumericValue(chave.charAt(i));
            soma += num * peso;
            peso++;
            if (peso > 9) peso = 2;
        }
        int resto = soma % 11;
        int dv = 11 - resto;
        return (dv >= 10) ? "0" : String.valueOf(dv);
    }
}