package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class NfceService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ConfiguracaoLojaService configuracaoLojaService;
    @Autowired private TributacaoService tributacaoService;

    // ==================================================================================
    // CONSULTA STATUS SEFAZ (PING)
    // ==================================================================================
    public String consultarStatusSefaz() throws Exception {
        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            return "SIMULACAO - Certificado ausente no banco de dados.";
        }

        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
        TRetConsStatServ retorno = Nfe.statusServico(configNfe, DocumentoEnum.NFCE);
        return "Status: " + retorno.getCStat() + " - " + retorno.getXMotivo();
    }

    // ==================================================================================
    // EMISSÃO PRINCIPAL DE NFC-E
    // ==================================================================================
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NfceResponseDTO emitirNfce(Venda venda) {
        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            log.warn("⚠️ Certificado ausente. Simulando emissão.");
            return simularEmissaoEmDev(venda);
        }
        try {
            return processarEmissao(venda, configLoja);
        } catch (Exception e) {
            log.error("Falha na emissão NFC-e: {}", e.getMessage());
            throw new ValidationException("Erro na emissão NFC-e: " + e.getMessage());
        }
    }

    private NfceResponseDTO processarEmissao(Venda venda, ConfiguracaoLoja configLoja) throws Exception {
        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);

        // 💥 O GRANDE TRUQUE: Desligamos a validação de Schema XSD Local para confiar apenas na SEFAZ.
        configNfe.setValidacaoDocumento(false);

        boolean isProducao = AmbienteEnum.PRODUCAO.equals(configNfe.getAmbiente());
        String serie = String.valueOf(isProducao ? configLoja.getFiscal().getSerieProducao() : configLoja.getFiscal().getSerieHomologacao());
        String nNF = String.valueOf(venda.getIdVenda() + 1000);
        String cNF = String.format("%08d", new Random().nextInt(99999999));
        String cnpjEmitente = configLoja.getLoja().getCnpj().replaceAll("\\D", "");

        // 1. Geração da Chave de Acesso
        String chaveSemDigito = gerarChaveAcesso(configNfe.getEstado().getCodigoUF(), LocalDateTime.now(), cnpjEmitente, "65", serie, nNF, "1", cNF);
        String dv = calcularDV(chaveSemDigito);
        String chaveAcesso = chaveSemDigito + dv;

        TEnviNFe enviNFe = new TEnviNFe();
        enviNFe.setVersao("4.00");
        enviNFe.setIdLote("1");
        enviNFe.setIndSinc("1");

        TNFe nfe = new TNFe();
        TNFe.InfNFe infNFe = new TNFe.InfNFe();
        infNFe.setId("NFe" + chaveAcesso);
        infNFe.setVersao("4.00");

        infNFe.setIde(montarIde(configNfe, cNF, nNF, dv, serie));
        infNFe.setEmit(montarEmit(configLoja));
        if (venda.getCliente() != null && venda.getCliente().getDocumento() != null) {
            infNFe.setDest(montarDest(venda.getCliente()));
        }

        infNFe.getDet().addAll(montarDetalhes(venda.getItens(), configLoja));
        infNFe.setTotal(montarTotal(venda));
        infNFe.setTransp(montarTransp());
        infNFe.setPag(montarPag(venda.getValorTotal(), venda.getTroco()));

        TNFe.InfNFe.InfAdic adic = new TNFe.InfNFe.InfAdic();
        adic.setInfCpl(tributacaoService.calcularTextoTransparencia(venda));
        infNFe.setInfAdic(adic);
        infNFe.setInfRespTec(montarRespTec(cnpjEmitente));

        nfe.setInfNFe(infNFe);
        enviNFe.getNFe().add(nfe);

        // 2. ASSINATURA OBRIGATÓRIA DA NFE (Sem QR Code para garantir a integridade da Assinatura)
        String xmlNaoAssinado = XmlNfeUtil.objectToXml(enviNFe);
        String xmlAssinado = br.com.swconsultoria.nfe.Assinar.assinaNfe(configNfe, xmlNaoAssinado, br.com.swconsultoria.nfe.dom.enuns.AssinaturaEnum.NFE);

        // O Java converte o XML assinado de volta num Objeto TEnviNFe
        TEnviNFe enviNFeAssinado = XmlNfeUtil.xmlToObject(xmlAssinado, TEnviNFe.class);

        // ====================================================================================
        // 3. MONTAGEM DO QR CODE 2.0 (NT 2018.005)
        // ====================================================================================
        String cscId = isProducao ? configLoja.getFiscal().getCscIdProducao() : configLoja.getFiscal().getCscIdHomologacao();
        String cscToken = isProducao ? configLoja.getFiscal().getTokenProducao() : configLoja.getFiscal().getTokenHomologacao();
        String qrCodeFront = "";

        if (cscId != null && !cscId.isBlank() && cscToken != null && !cscToken.isBlank()) {
            String cIdToken = String.format("%06d", Integer.parseInt(cscId.replaceAll("\\D", "")));

            // URLs Oficiais de Pernambuco - HTTP é exigido em Homologação
            String urlQrCode = isProducao
                    ? "http://nfce.sefaz.pe.gov.br/nfce/consulta"
                    : "http://nfcehomolog.sefaz.pe.gov.br/nfce/consulta";

            String urlChave = "www.sefaz.pe.gov.br/nfce/consulta";

            String pSemHash = chaveAcesso + "|2|" + configNfe.getAmbiente().getCodigo() + "|" + cIdToken;

            // A Hash gerada
            String hash = gerarHashSHA1(pSemHash + cscToken.trim());

            qrCodeFront = urlQrCode + "?p=" + pSemHash + "|" + hash;

            TNFe.InfNFeSupl supl = new TNFe.InfNFeSupl();

            // Atribuímos diretamente o CDATA no objeto (o JAXB preserva isso)
            supl.setQrCode("<![CDATA[" + qrCodeFront + "]]>");
            supl.setUrlChave(urlChave);

            enviNFeAssinado.getNFe().get(0).setInfNFeSupl(supl);
        } else {
            log.warn("⚠️ CSC ID ou Token não configurados. A SEFAZ rejeitará a nota sem QR Code.");
        }

        // ====================================================================================
        // 4. ENVIO PARA A SEFAZ (O compilador agora aprova porque passamos o TEnviNFe)
        // ====================================================================================
        TRetEnviNFe retorno;
        try {
            retorno = Nfe.enviarNfe(configNfe, enviNFeAssinado, DocumentoEnum.NFCE);
        } catch (Exception e) {
            log.error("Erro interno do envio SEFAZ: {}", e.getMessage());
            throw new ValidationException("Erro SEFAZ: " + e.getMessage());
        }

        if (retorno.getProtNFe() != null && "100".equals(retorno.getProtNFe().getInfProt().getCStat())) {
            venda.setStatusNfce(StatusFiscal.AUTORIZADA);
            venda.setChaveAcessoNfce(chaveAcesso);

            venda.setXmlNota(XmlNfeUtil.criaNfeProc(enviNFeAssinado, retorno.getProtNFe()));
            vendaRepository.save(venda);

            return new NfceResponseDTO(venda.getIdVenda(), "100", "Autorizada", chaveAcesso, "", venda.getXmlNota(), qrCodeFront);
        } else {
            String msg = retorno.getProtNFe() != null ? retorno.getProtNFe().getInfProt().getXMotivo() : retorno.getXMotivo();
            log.error("Rejeição SEFAZ: {}", msg);
            throw new ValidationException("Sefaz Rejeitou: " + msg);
        }
    }

    private String gerarHashSHA1(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] result = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : result) sb.append(String.format("%02X", b));

        return sb.toString();
    }

    private String gerarChaveAcesso(String cuf, LocalDateTime d, String cnpj, String mod, String ser, String nnf, String tp, String cnf) {
        return String.format("%02d%s%s%s%03d%09d%s%s", Integer.parseInt(cuf), d.format(DateTimeFormatter.ofPattern("yyMM")), cnpj, mod, Integer.parseInt(ser), Long.parseLong(nnf), tp, cnf);
    }

    private String calcularDV(String chave) {
        int soma = 0, peso = 2;
        for (int i = chave.length() - 1; i >= 0; i--) {
            soma += Character.getNumericValue(chave.charAt(i)) * peso;
            if (++peso > 9) peso = 2;
        }
        int resto = soma % 11;
        return (resto == 0 || resto == 1) ? "0" : String.valueOf(11 - resto);
    }

    private ConfiguracoesNfe iniciarConfiguracoesNfe(ConfiguracaoLoja loja) throws Exception {
        Certificado cert = CertificadoService.certificadoPfxBytes(loja.getFiscal().getArquivoCertificado(), loja.getFiscal().getSenhaCert());
        AmbienteEnum amb = "PRODUCAO".equalsIgnoreCase(loja.getFiscal().getAmbiente()) ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO;
        String schemas = System.getProperty("user.dir") + "/src/main/resources/schemas";
        return ConfiguracoesNfe.criarConfiguracoes(EstadosEnum.PE, amb, cert, schemas);
    }

    private TNFe.InfNFe.Ide montarIde(ConfiguracoesNfe cfg, String cnf, String nnf, String dv, String serie) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(cfg.getEstado().getCodigoUF());
        ide.setCNF(cnf);
        ide.setNatOp("VENDA CONSUMIDOR");
        ide.setMod("65");
        ide.setSerie(serie);
        ide.setNNF(nnf);
        ide.setDhEmi(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG("2611606");
        ide.setTpImp("4");
        ide.setTpEmis("1");
        ide.setCDV(dv);
        ide.setTpAmb(cfg.getAmbiente().getCodigo());
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("1.0");
        return ide;
    }

    private TNFe.InfNFe.Emit montarEmit(ConfiguracaoLoja cfg) {
        TNFe.InfNFe.Emit e = new TNFe.InfNFe.Emit();
        e.setCNPJ(cfg.getLoja().getCnpj().replaceAll("\\D", ""));
        e.setXNome(cfg.getLoja().getRazaoSocial());
        e.setIE(cfg.getLoja().getIe().replaceAll("\\D", ""));
        e.setCRT(cfg.getFiscal().getRegime() != null ? cfg.getFiscal().getRegime() : "1");
        TEnderEmi end = new TEnderEmi();
        end.setXLgr(cfg.getEndereco().getLogradouro());
        end.setNro(cfg.getEndereco().getNumero());
        end.setXBairro(cfg.getEndereco().getBairro());
        end.setCMun("2611606");
        end.setXMun("RECIFE");
        end.setUF(TUfEmi.PE);
        end.setCEP(cfg.getEndereco().getCep().replaceAll("\\D", ""));
        end.setCPais("1058");
        end.setXPais("BRASIL");
        e.setEnderEmit(end);
        return e;
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itens, ConfiguracaoLoja config) {
        List<TNFe.InfNFe.Det> details = new ArrayList<>();
        boolean isST = config.getFiscal().getNaturezaPadrao() != null && config.getFiscal().getNaturezaPadrao().contains("5.405");
        int i = 1;
        for (ItemVenda item : itens) {
            TNFe.InfNFe.Det det = new TNFe.InfNFe.Det();
            det.setNItem(String.valueOf(i++));
            TNFe.InfNFe.Det.Prod p = new TNFe.InfNFe.Det.Prod();
            p.setCProd(item.getProduto().getId().toString());
            p.setCEAN("SEM GTIN");
            p.setXProd(item.getProduto().getDescricao());

            String ncm = item.getProduto().getNcm() != null ? item.getProduto().getNcm().replaceAll("\\D", "") : "33049990";
            if (ncm.length() != 8) ncm = "33049990";
            p.setNCM(ncm);

            if (item.getProduto().getCest() != null && !item.getProduto().getCest().isBlank()) {
                p.setCEST(item.getProduto().getCest().replaceAll("\\D", ""));
            }

            p.setCFOP(isST ? "5405" : "5102");
            p.setUCom("UN");
            p.setQCom(item.getQuantidade().setScale(4, RoundingMode.HALF_UP).toString());
            p.setVUnCom(item.getPrecoUnitario().setScale(2, RoundingMode.HALF_UP).toString());
            p.setVProd(item.getPrecoUnitario().multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP).toString());
            p.setCEANTrib("SEM GTIN");
            p.setUTrib("UN");
            p.setQTrib(p.getQCom());
            p.setVUnTrib(p.getVUnCom());
            p.setIndTot("1");
            det.setProd(p);

            TNFe.InfNFe.Det.Imposto imp = new TNFe.InfNFe.Det.Imposto();
            TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();

            if (isST) {
                TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN500 s500 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN500();
                s500.setOrig(item.getProduto().getOrigem() != null ? item.getProduto().getOrigem() : "0");
                s500.setCSOSN("500");
                icms.setICMSSN500(s500);
            } else {
                TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 s102 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
                s102.setOrig(item.getProduto().getOrigem() != null ? item.getProduto().getOrigem() : "0");
                s102.setCSOSN("102");
                icms.setICMSSN102(s102);
            }

            imp.getContent().add(new ObjectFactory().createTNFeInfNFeDetImpostoICMS(icms));
            det.setImposto(imp);
            details.add(det);
        }
        return details;
    }

    private TNFe.InfNFe.Total montarTotal(Venda v) {
        TNFe.InfNFe.Total t = new TNFe.InfNFe.Total();
        TNFe.InfNFe.Total.ICMSTot icms = new TNFe.InfNFe.Total.ICMSTot();
        String val = v.getValorTotal().setScale(2, RoundingMode.HALF_UP).toString();
        icms.setVBC("0.00"); icms.setVICMS("0.00"); icms.setVProd(val); icms.setVNF(val);
        icms.setVICMSDeson("0.00"); icms.setVFCP("0.00"); icms.setVBCST("0.00"); icms.setVST("0.00");
        icms.setVFCPST("0.00"); icms.setVFCPSTRet("0.00"); icms.setVFrete("0.00"); icms.setVSeg("0.00");
        icms.setVDesc("0.00"); icms.setVII("0.00"); icms.setVIPI("0.00"); icms.setVIPIDevol("0.00");
        icms.setVPIS("0.00"); icms.setVCOFINS("0.00"); icms.setVOutro("0.00");
        t.setICMSTot(icms);
        return t;
    }

    private TNFe.InfNFe.Pag montarPag(BigDecimal tot, BigDecimal troco) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
        det.setTPag("01");

        BigDecimal vTroco = troco != null ? troco : BigDecimal.ZERO;
        BigDecimal vPag = tot.add(vTroco);

        det.setVPag(vPag.setScale(2, RoundingMode.HALF_UP).toString());
        pag.getDetPag().add(det);
        pag.setVTroco(vTroco.setScale(2, RoundingMode.HALF_UP).toString());

        return pag;
    }

    private TNFe.InfNFe.Dest montarDest(Cliente c) {
        TNFe.InfNFe.Dest d = new TNFe.InfNFe.Dest();
        d.setXNome(c.getNome());
        String doc = c.getDocumento().replaceAll("\\D", "");
        if (doc.length() == 11) d.setCPF(doc); else d.setCNPJ(doc);
        return d;
    }

    private TNFe.InfNFe.Transp montarTransp() {
        TNFe.InfNFe.Transp t = new TNFe.InfNFe.Transp();
        t.setModFrete("9");
        return t;
    }

    private TInfRespTec montarRespTec(String cnpj) {
        TInfRespTec r = new TInfRespTec();
        r.setCNPJ(cnpj);
        r.setXContato("Suporte TI");
        r.setEmail("suporte@ddcosmeticos.com.br");
        r.setFone("81999999999");
        return r;
    }

    private NfceResponseDTO simularEmissaoEmDev(Venda v) {
        return new NfceResponseDTO(v.getIdVenda(), "100", "Simulado", "35230900000000000000650010000000011000000001", "123", "", "");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transmitirNotaContingencia(Venda venda) {
        try {
            log.info("📡 Tentando transmitir nota pendente/contingência ID: {}", venda.getIdVenda());
            ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();

            if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
                log.warn("⚠️ Certificado ausente. Ignorando retransmissão da venda {}", venda.getIdVenda());
                return;
            }

            processarEmissao(venda, configLoja);
            log.info("✅ Nota ID {} transmitida e autorizada com sucesso na SEFAZ.", venda.getIdVenda());
        } catch (Exception e) {
            log.error("❌ Falha na transmissão automática da venda {}: {}", venda.getIdVenda(), e.getMessage());
        }
    }
}