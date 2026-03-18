package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEmissao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PagamentoVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.schema_4.consStatServ.TRetConsStatServ;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class NfceService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ConfiguracaoLojaService configuracaoLojaService; // A FONTE ÚNICA DE VERDADE

    @Autowired
    private TributacaoService tributacaoService;

    // =========================================================================
    // STATUS SEFAZ (PING NFC-e)
    // =========================================================================
    public String consultarStatusSefaz() throws Exception {
        // Agora busca a configuração exata que foi salva, independente do ID no banco
        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();

        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            log.warn("Certificado não encontrado na base de dados para a NFC-e.");
            return "Ambiente: SIMULACAO (Sem Certificado) | Status: 107 - Serviço em Operação (Simulado)";
        }

        try {
            ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
            TRetConsStatServ retorno = Nfe.statusServico(configNfe, DocumentoEnum.NFCE);

            return "Ambiente: " + configNfe.getAmbiente() +
                    " | Status: " + retorno.getCStat() +
                    " - " + retorno.getXMotivo();

        } catch (Exception e) {
            log.error("Erro ao consultar Status SEFAZ NFC-e: ", e);
            throw new ValidationException("Falha de comunicação com a SEFAZ: " + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NfceResponseDTO emitirNfce(Venda venda) {
        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();

        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            return simularEmissaoEmDev(venda, configLoja);
        }

        try {
            return processarEmissao(venda, configLoja, TipoEmissao.NORMAL);
        } catch (Exception e) {
            log.error("🔴 Falha na emissão Online. Tentando Contingência Offline. Erro: {}", e.getMessage());
            if (isErroDeConexao(e)) {
                try {
                    return processarEmissao(venda, configLoja, TipoEmissao.OFFLINE);
                } catch (Exception exContingencia) {
                    log.error("💀 Falha crítica até na contingência!", exContingencia);
                    throw new ValidationException("Erro Crítico na Contingência: " + exContingencia.getMessage());
                }
            } else {
                throw new ValidationException("Erro na emissão NFC-e: " + e.getMessage());
            }
        }
    }

    private NfceResponseDTO processarEmissao(Venda venda, ConfiguracaoLoja configLoja, TipoEmissao tipoEmissao) throws Exception {
        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);

        String modelo = "65";
        String serie = String.valueOf(configLoja.getFiscal().getSerieProducao());
        if (!configNfe.getAmbiente().equals(AmbienteEnum.PRODUCAO)) {
            serie = String.valueOf(configLoja.getFiscal().getSerieHomologacao());
        }

        String nNF = String.valueOf(venda.getIdVenda() + 1000);
        String cnf = String.format("%08d", new Random().nextInt(99999999));
        String cnpjEmitente = (configLoja.getLoja() != null && configLoja.getLoja().getCnpj() != null) ? configLoja.getLoja().getCnpj().replaceAll("\\D", "") : "00000000000000";
        String tpEmisStr = tipoEmissao.getCodigo();

        String chaveSemDigito = gerarChaveAcesso(configNfe.getEstado().getCodigoUF(), LocalDateTime.now(), cnpjEmitente, modelo, serie, nNF, tpEmisStr, cnf);
        String dv = calcularDV(chaveSemDigito);
        String chaveAcesso = chaveSemDigito + dv;

        TNFe nfe = new TNFe();
        TNFe.InfNFe infNFe = new TNFe.InfNFe();
        infNFe.setId("NFe" + chaveAcesso);
        infNFe.setVersao("4.00");

        infNFe.setIde(montarIde(configNfe, cnf, nNF, dv, modelo, serie, tpEmisStr));
        infNFe.setEmit(montarEmit(configLoja));

        if (venda.getCliente() != null) infNFe.setDest(montarDest(venda.getCliente()));

        infNFe.getDet().addAll(montarDetalhes(venda.getItens()));
        infNFe.setTotal(montarTotal(venda));
        infNFe.setTransp(montarTransp());
        infNFe.setPag(montarPag(venda.getPagamentos(), venda.getValorTotal()));

        TNFe.InfNFe.InfAdic infAdic = new TNFe.InfNFe.InfAdic();
        infAdic.setInfCpl(tributacaoService.calcularTextoTransparencia(venda));
        infNFe.setInfAdic(infAdic);
        nfe.setInfNFe(infNFe);

        String status = ""; String motivo = ""; String protocolo = ""; String xmlFinal = "";

        if (tipoEmissao == TipoEmissao.OFFLINE) {
            status = "100"; motivo = "Emitida em Contingência Offline";
            xmlFinal = "XML ASSINADO PENDENTE DE ENVIO (SIMULADO)";
            venda.setStatusNfce(StatusFiscal.CONTINGENCIA);
            venda.setMensagemRejeicao("Aguardando internet para transmissão.");
            vendaRepository.save(venda);
        } else {
            TEnviNFe enviNFe = new TEnviNFe();
            enviNFe.setVersao("4.00"); enviNFe.setIdLote("1"); enviNFe.setIndSinc("1"); enviNFe.getNFe().add(nfe);

            TRetEnviNFe retorno = Nfe.enviarNfe(configNfe, enviNFe, DocumentoEnum.NFCE);
            status = retorno.getProtNFe().getInfProt().getCStat();
            motivo = retorno.getProtNFe().getInfProt().getXMotivo();
            if (retorno.getProtNFe().getInfProt().getNProt() != null) protocolo = retorno.getProtNFe().getInfProt().getNProt();
            xmlFinal = XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe());

            if (!status.equals("100")) throw new ValidationException("Rejeição SEFAZ: " + status + " - " + motivo);
            venda.setStatusNfce(StatusFiscal.AUTORIZADA);
            vendaRepository.save(venda);
        }

        String cscId = "PRODUCAO".equalsIgnoreCase(configLoja.getFiscal().getAmbiente()) ? configLoja.getFiscal().getCscIdProducao() : configLoja.getFiscal().getCscIdHomologacao();
        String urlQrCode = "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx?p=" + chaveAcesso + "|2|1|1|" + cscId;

        return new NfceResponseDTO(venda.getIdVenda(), status, motivo, chaveAcesso, protocolo, xmlFinal, urlQrCode);
    }

    private ConfiguracoesNfe iniciarConfiguracoesNfe(ConfiguracaoLoja loja) throws Exception {
        byte[] certificadoBytes = loja.getFiscal().getArquivoCertificado();
        String senha = loja.getFiscal().getSenhaCert();
        Certificado certificado = CertificadoService.certificadoPfxBytes(certificadoBytes, senha);
        EstadosEnum estado = EstadosEnum.valueOf(loja.getEndereco().getUf());
        AmbienteEnum ambiente = "PRODUCAO".equalsIgnoreCase(loja.getFiscal().getAmbiente()) ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO;
        return ConfiguracoesNfe.criarConfiguracoes(estado, ambiente, certificado, "schemas");
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itensVenda) {
        List<TNFe.InfNFe.Det> detalhes = new ArrayList<>();
        ObjectFactory factory = new ObjectFactory();
        int numeroItem = 1;

        for (ItemVenda item : itensVenda) {
            TNFe.InfNFe.Det det = new TNFe.InfNFe.Det();
            det.setNItem(String.valueOf(numeroItem));

            TNFe.InfNFe.Det.Prod prod = new TNFe.InfNFe.Det.Prod();
            prod.setCProd(item.getProduto().getId().toString());
            prod.setCEAN("SEM GTIN");
            prod.setXProd(item.getProduto().getDescricao());
            prod.setNCM((item.getProduto().getNcm() != null) ? item.getProduto().getNcm().replaceAll("\\D", "") : "00000000");
            if (item.getProduto().getCest() != null) prod.setCEST(item.getProduto().getCest().replaceAll("\\D", ""));
            prod.setCFOP("5102");
            String uCom = item.getProduto().getUnidade() != null ? item.getProduto().getUnidade() : "UN";
            prod.setUCom(uCom);
            prod.setQCom(item.getQuantidade().setScale(4, RoundingMode.HALF_UP).toString());
            prod.setVUnCom(item.getPrecoUnitario().setScale(2, RoundingMode.HALF_UP).toString());
            prod.setVProd(item.getPrecoUnitario().multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP).toString());
            prod.setCEANTrib("SEM GTIN"); prod.setUTrib(uCom); prod.setQTrib(prod.getQCom()); prod.setVUnTrib(prod.getVUnCom()); prod.setIndTot("1");
            det.setProd(prod);

            TNFe.InfNFe.Det.Imposto imposto = new TNFe.InfNFe.Det.Imposto();

            TNFe.InfNFe.Det.Imposto.ICMS icmsWrapper = new TNFe.InfNFe.Det.Imposto.ICMS();
            TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 icms102 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
            icms102.setOrig("0"); icms102.setCSOSN("102");
            icmsWrapper.setICMSSN102(icms102);
            imposto.getContent().add(factory.createTNFeInfNFeDetImpostoICMS(icmsWrapper));

            TNFe.InfNFe.Det.Imposto.PIS pisWrapper = new TNFe.InfNFe.Det.Imposto.PIS();
            TNFe.InfNFe.Det.Imposto.PIS.PISOutr pisOutr = new TNFe.InfNFe.Det.Imposto.PIS.PISOutr();
            pisOutr.setCST("99"); pisOutr.setVBC("0.00"); pisOutr.setPPIS("0.00"); pisOutr.setVPIS("0.00");
            pisWrapper.setPISOutr(pisOutr);
            imposto.getContent().add(factory.createTNFeInfNFeDetImpostoPIS(pisWrapper));

            TNFe.InfNFe.Det.Imposto.COFINS cofinsWrapper = new TNFe.InfNFe.Det.Imposto.COFINS();
            TNFe.InfNFe.Det.Imposto.COFINS.COFINSOutr cofinsOutr = new TNFe.InfNFe.Det.Imposto.COFINS.COFINSOutr();
            cofinsOutr.setCST("99"); cofinsOutr.setVBC("0.00"); cofinsOutr.setPCOFINS("0.00"); cofinsOutr.setVCOFINS("0.00");
            cofinsWrapper.setCOFINSOutr(cofinsOutr);
            imposto.getContent().add(factory.createTNFeInfNFeDetImpostoCOFINS(cofinsWrapper));

            det.setImposto(imposto);
            detalhes.add(det);
            numeroItem++;
        }
        return detalhes;
    }

    private TNFe.InfNFe.Total montarTotal(Venda venda) {
        String totalStr = venda.getValorTotal().setScale(2, RoundingMode.HALF_UP).toString();
        TNFe.InfNFe.Total total = new TNFe.InfNFe.Total();
        TNFe.InfNFe.Total.ICMSTot icmsTot = new TNFe.InfNFe.Total.ICMSTot();
        icmsTot.setVBC("0.00"); icmsTot.setVICMS("0.00"); icmsTot.setVICMSDeson("0.00");
        icmsTot.setVFCP("0.00"); icmsTot.setVBCST("0.00"); icmsTot.setVST("0.00");
        icmsTot.setVFCPST("0.00"); icmsTot.setVFCPSTRet("0.00"); icmsTot.setVProd(totalStr);
        icmsTot.setVFrete("0.00"); icmsTot.setVSeg("0.00"); icmsTot.setVDesc("0.00");
        icmsTot.setVII("0.00"); icmsTot.setVIPI("0.00"); icmsTot.setVIPIDevol("0.00");
        icmsTot.setVPIS("0.00"); icmsTot.setVCOFINS("0.00"); icmsTot.setVOutro("0.00");
        icmsTot.setVNF(totalStr);
        total.setICMSTot(icmsTot);
        return total;
    }

    private NfceResponseDTO simularEmissaoEmDev(Venda venda, ConfiguracaoLoja config) {
        String cnpj = "00000000000000";
        if (config.getLoja() != null && config.getLoja().getCnpj() != null) cnpj = config.getLoja().getCnpj().replaceAll("\\D", "");
        String chaveFake = "352309" + cnpj + "650010000000011" + String.format("%09d", venda.getIdVenda());
        return new NfceResponseDTO(venda.getIdVenda(), "100", "EMISSAO SIMULADA", chaveFake, "1234567890", "<xml>Simulacao</xml>", "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx");
    }

    private boolean isErroDeConexao(Exception e) {
        String msg = e.getMessage().toLowerCase();
        return msg.contains("timeout") || msg.contains("refused") || msg.contains("host") || msg.contains("network") || msg.contains("conexão");
    }

    private TNFe.InfNFe.Ide montarIde(ConfiguracoesNfe config, String cnf, String nNF, String dv, String modelo, String serie, String tpEmis) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(config.getEstado().getCodigoUF()); ide.setCNF(cnf); ide.setNatOp("VENDA CONSUMIDOR");
        ide.setMod(modelo); ide.setSerie(serie); ide.setNNF(nNF); ide.setDhEmi(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setTpNF("1"); ide.setIdDest("1"); ide.setCMunFG("2611606"); ide.setTpImp("4");
        ide.setTpEmis(tpEmis); ide.setCDV(dv); ide.setTpAmb(config.getAmbiente().getCodigo());
        ide.setFinNFe("1"); ide.setIndFinal("1"); ide.setIndPres("1"); ide.setProcEmi("0"); ide.setVerProc("1.0");
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
        }
        emit.setCRT("1");
        TEnderEmi ender = new TEnderEmi();
        if (config.getEndereco() != null) {
            ender.setXLgr(config.getEndereco().getLogradouro());
            ender.setNro(config.getEndereco().getNumero());
            ender.setXBairro(config.getEndereco().getBairro());
            ender.setCMun("2611606");
            ender.setXMun("RECIFE");
            ender.setUF(TUfEmi.PE);
            ender.setCEP(config.getEndereco().getCep() != null ? config.getEndereco().getCep().replaceAll("\\D", "") : "00000000");
        }
        ender.setCPais("1058"); ender.setXPais("BRASIL"); emit.setEnderEmit(ender);
        return emit;
    }

    private TNFe.InfNFe.Dest montarDest(br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente cliente) {
        TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();
        dest.setXNome(cliente.getNome());
        if (cliente.getDocumento() != null) {
            String doc = cliente.getDocumento().replaceAll("\\D", "");
            if (doc.length() == 11) dest.setCPF(doc);
            else if (doc.length() == 14) dest.setCNPJ(doc);
        }
        return dest;
    }

    private TNFe.InfNFe.Transp montarTransp() {
        TNFe.InfNFe.Transp t = new TNFe.InfNFe.Transp();
        t.setModFrete("9");
        return t;
    }

    private TNFe.InfNFe.Pag montarPag(List<PagamentoVenda> pags, BigDecimal total) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
        det.setTPag("01"); det.setVPag(total != null ? total.toString() : "0.00");
        pag.getDetPag().add(det);
        return pag;
    }

    private String gerarChaveAcesso(String cuf, LocalDateTime data, String cnpj, String mod, String serie, String nnf, String tpEmis, String cnf) {
        StringBuilder chave = new StringBuilder();
        chave.append(String.format("%02d", Integer.parseInt(cuf)));
        chave.append(data.format(DateTimeFormatter.ofPattern("yyMM")));
        chave.append(cnpj); chave.append(mod);
        chave.append(String.format("%03d", Integer.parseInt(serie)));
        chave.append(String.format("%09d", Long.parseLong(nnf)));
        chave.append(tpEmis); chave.append(cnf);
        return chave.toString();
    }

    private String calcularDV(String chave) {
        int soma = 0; int peso = 2;
        for (int i = chave.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(chave.charAt(i)) * peso;
            peso++;
            if (peso > 9) peso = 2;
        }
        int resto = soma % 11;
        int dv = 11 - resto;
        return (dv >= 10) ? "0" : String.valueOf(dv);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transmitirNotaContingencia(Venda venda) {
        try {
            log.info("📡 SCHEDULER: Tentando transmitir nota de contingência ID: {}", venda.getIdVenda());
            ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
            processarEmissao(venda, configLoja, TipoEmissao.NORMAL);
            log.info("✅ Nota ID {} recuperada da contingência e autorizada com sucesso!", venda.getIdVenda());
        } catch (Exception e) {
            log.error("❌ Falha ao transmitir nota de contingência {}: {}", venda.getIdVenda(), e.getMessage());
        }
    }
}