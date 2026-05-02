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

    public String consultarStatusSefaz() throws Exception {
        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            return "SIMULACAO - Certificado ausente no banco de dados.";
        }

        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
        TRetConsStatServ retorno = Nfe.statusServico(configNfe, DocumentoEnum.NFCE);
        return "Status: " + retorno.getCStat() + " - " + retorno.getXMotivo();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NfceResponseDTO emitirNfce(Venda vendaDesanexada) {
        // ✅ CORREÇÃO: Utilizando o método que garante o JOIN FETCH dos itens e pagamentos
        Venda venda = vendaRepository.findByIdComItens(vendaDesanexada.getIdVenda())
                .orElseThrow(() -> new ValidationException("Venda não encontrada para emissão."));

        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            log.warn("⚠️ Certificado ausente. Cancelando emissão real.");
            return null;
        }

        try {
            return processarEmissao(venda, configLoja, "1", true);
        } catch (ValidationException ve) {
            throw ve;
        } catch (Exception e) {
            log.warn("⚠️ Falha ao emitir NFCe: {}", e.getMessage());
            throw new ValidationException("Erro na emissão: " + e.getMessage());
        }
    }

    private NfceResponseDTO processarEmissao(Venda venda, ConfiguracaoLoja configLoja, String tpEmis, boolean validarSchema) throws Exception {
        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
        configNfe.setValidacaoDocumento(validarSchema);

        boolean isProducao = AmbienteEnum.PRODUCAO.equals(configNfe.getAmbiente());
        String serie = String.valueOf(isProducao ? configLoja.getFiscal().getSerieProducao() : configLoja.getFiscal().getSerieHomologacao());
        String nNF = String.valueOf(venda.getIdVenda() + 1000);
        String cNF = String.format("%08d", new Random().nextInt(99999999));
        String cnpjEmitente = configLoja.getLoja().getCnpj().replaceAll("\\D", "");

        String chaveSemDigito = gerarChaveAcesso(configNfe.getEstado().getCodigoUF(), LocalDateTime.now(), cnpjEmitente, "65", serie, nNF, tpEmis, cNF);
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

        infNFe.setIde(montarIde(configNfe, cNF, nNF, dv, serie, tpEmis));
        infNFe.setEmit(montarEmit(configLoja));
        if (venda.getCliente() != null && venda.getCliente().getDocumento() != null) {
            infNFe.setDest(montarDest(venda.getCliente()));
        }

        infNFe.getDet().addAll(montarDetalhes(venda.getItens(), configLoja, isProducao));
        infNFe.setTotal(montarTotal(venda));
        infNFe.setTransp(montarTransp());
        infNFe.setPag(montarPag(venda.getPagamentos(), venda.getValorTotal(), venda.getTroco()));

        TNFe.InfNFe.InfAdic adic = new TNFe.InfNFe.InfAdic();
        adic.setInfCpl("Trib aprox R$ 0.00 Fed e R$ 0.00 Est. Fonte: IBPT.");
        infNFe.setInfAdic(adic);
        infNFe.setInfRespTec(montarRespTec(cnpjEmitente));

        nfe.setInfNFe(infNFe);

        String cscId = isProducao ? configLoja.getFiscal().getCscIdProducao() : configLoja.getFiscal().getCscIdHomologacao();
        String cscToken = isProducao ? configLoja.getFiscal().getTokenProducao() : configLoja.getFiscal().getTokenHomologacao();

        if (cscId == null || cscToken == null) {
            throw new ValidationException("CSC (Token/ID) de " + (isProducao ? "Produção" : "Homologação") + " não configurado na Loja.");
        }

        String qrCodeStr = gerarQrCodeSefazPE(chaveAcesso, configNfe.getAmbiente().getCodigo(), cscId, cscToken);

        TNFe.InfNFeSupl infNFeSupl = new TNFe.InfNFeSupl();
        infNFeSupl.setQrCode(qrCodeStr);

        infNFeSupl.setUrlChave("nfce.sefaz.pe.gov.br/nfce/consulta");

        nfe.setInfNFeSupl(infNFeSupl);

        enviNFe.getNFe().add(nfe);

        String xmlNaoAssinado = XmlNfeUtil.objectToXml(enviNFe);
        String xmlAssinado = br.com.swconsultoria.nfe.Assinar.assinaNfe(configNfe, xmlNaoAssinado, br.com.swconsultoria.nfe.dom.enuns.AssinaturaEnum.NFE);
        TEnviNFe enviNFeAssinado = XmlNfeUtil.xmlToObject(xmlAssinado, TEnviNFe.class);

        TRetEnviNFe retorno = Nfe.enviarNfe(configNfe, enviNFeAssinado, DocumentoEnum.NFCE);

        if (retorno.getProtNFe() != null && "100".equals(retorno.getProtNFe().getInfProt().getCStat())) {
            venda.setStatusNfce(StatusFiscal.AUTORIZADA);
            venda.setChaveAcessoNfce(chaveAcesso);
            venda.setXmlNota(XmlNfeUtil.criaNfeProc(enviNFeAssinado, retorno.getProtNFe()));
            venda.setUrlQrCode(qrCodeStr);
            vendaRepository.save(venda);
            return new NfceResponseDTO(venda.getIdVenda(), "100", "Autorizada", chaveAcesso, "", venda.getXmlNota(), qrCodeStr);
        } else {
            String msg = retorno.getProtNFe() != null ? retorno.getProtNFe().getInfProt().getXMotivo() : retorno.getXMotivo();
            throw new ValidationException("Sefaz Rejeitou: " + msg);
        }
    }

    private String gerarQrCodeSefazPE(String chaveAcesso, String tpAmbiente, String idCsc, String cscToken) throws Exception {
        String urlBase = tpAmbiente.equals("1")
                ? "http://nfce.sefaz.pe.gov.br/nfce/consulta"
                : "http://nfcehomolog.sefaz.pe.gov.br/nfce/consulta";

        String idCscLimpo = idCsc.replaceFirst("^0+(?!$)", "");
        String stringParaHash = chaveAcesso + "|2|" + tpAmbiente + "|" + idCscLimpo;
        String hashSha1 = gerarHashSHA1(stringParaHash + cscToken).toUpperCase();

        return urlBase + "?p=" + stringParaHash + "|" + hashSha1;
    }

    private String gerarHashSHA1(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] result = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : result) sb.append(String.format("%02x", b));
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

    private TNFe.InfNFe.Ide montarIde(ConfiguracoesNfe cfg, String cnf, String nnf, String dv, String serie, String tpEmis) {
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
        ide.setTpEmis(tpEmis);
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
        end.setXLgr(cfg.getEndereco().getLogradouro() != null ? cfg.getEndereco().getLogradouro() : "RUA");
        end.setNro(cfg.getEndereco().getNumero() != null ? cfg.getEndereco().getNumero() : "SN");
        end.setXBairro(cfg.getEndereco().getBairro() != null ? cfg.getEndereco().getBairro() : "CENTRO");
        end.setCMun("2611606");
        end.setXMun("RECIFE");
        end.setUF(TUfEmi.PE);
        end.setCEP(cfg.getEndereco().getCep() != null ? cfg.getEndereco().getCep().replaceAll("\\D", "") : "50000000");
        end.setCPais("1058");
        end.setXPais("BRASIL");
        e.setEnderEmit(end);
        return e;
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itens, ConfiguracaoLoja config, boolean isProducao) {
        List<TNFe.InfNFe.Det> details = new ArrayList<>();
        int i = 1;
        for (ItemVenda item : itens) {
            TNFe.InfNFe.Det det = new TNFe.InfNFe.Det();
            det.setNItem(String.valueOf(i));
            TNFe.InfNFe.Det.Prod p = new TNFe.InfNFe.Det.Prod();
            p.setCProd(item.getProduto().getId().toString());

            String ean = item.getProduto().getCodigoBarras();
            if (ean != null && ean.matches("\\d{8}|\\d{12}|\\d{13}|\\d{14}") && isValidGTIN(ean)) {
                p.setCEAN(ean);
                p.setCEANTrib(ean);
            } else {
                p.setCEAN("SEM GTIN");
                p.setCEANTrib("SEM GTIN");
            }

            if (!isProducao && i == 1) {
                p.setXProd("NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL");
            } else {
                p.setXProd(item.getDescricaoProduto() != null ? item.getDescricaoProduto() : item.getProduto().getDescricao());
            }

            String ncm = item.getProduto().getNcm() != null ? item.getProduto().getNcm().replaceAll("\\D", "") : "33049990";
            if (ncm.length() != 8) ncm = "33049990";
            p.setNCM(ncm);

            p.setCFOP("5102");
            p.setUCom("UN");
            p.setQCom(item.getQuantidade().setScale(4, RoundingMode.HALF_UP).toString());
            p.setVUnCom(item.getPrecoUnitario().setScale(2, RoundingMode.HALF_UP).toString());
            p.setVProd(item.getPrecoUnitario().multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP).toString());

            p.setUTrib("UN");
            p.setQTrib(p.getQCom());
            p.setVUnTrib(p.getVUnCom());
            p.setIndTot("1");
            det.setProd(p);

            TNFe.InfNFe.Det.Imposto imp = new TNFe.InfNFe.Det.Imposto();
            TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();
            TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 s102 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
            s102.setOrig(item.getProduto().getOrigem() != null ? item.getProduto().getOrigem() : "0");
            s102.setCSOSN("102");
            icms.setICMSSN102(s102);
            imp.getContent().add(new ObjectFactory().createTNFeInfNFeDetImpostoICMS(icms));
            det.setImposto(imp);
            details.add(det);
            i++;
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

    private TNFe.InfNFe.Pag montarPag(List<PagamentoVenda> pagamentos, BigDecimal tot, BigDecimal troco) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();

        if (pagamentos == null || pagamentos.isEmpty()) {
            TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
            det.setTPag("01");
            det.setVPag(tot.add(troco != null ? troco : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toString());
            pag.getDetPag().add(det);
        } else {
            for (PagamentoVenda p : pagamentos) {
                TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
                String tPag = "01";

                if (p.getFormaPagamento() != null) {
                    switch (p.getFormaPagamento().name().toUpperCase()) {
                        case "PIX": tPag = "17"; break;
                        case "CREDITO": tPag = "03"; break;
                        case "DEBITO": tPag = "04"; break;
                        case "FIADO":
                        case "CREDIARIO":
                            tPag = "05";
                            det.setIndPag("1");
                            break;
                        default: tPag = "01"; break;
                    }
                }

                det.setTPag(tPag);
                det.setVPag(p.getValor().setScale(2, RoundingMode.HALF_UP).toString());

                if (tPag.equals("03") || tPag.equals("04") || tPag.equals("17")) {
                    TNFe.InfNFe.Pag.DetPag.Card card = new TNFe.InfNFe.Pag.DetPag.Card();
                    card.setTpIntegra("2");
                    det.setCard(card);
                }

                pag.getDetPag().add(det);
            }
        }

        BigDecimal vTroco = troco != null ? troco : BigDecimal.ZERO;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transmitirNotaContingencia(Venda vendaDesanexada) {
        try {
            Venda venda = vendaRepository.findByIdComItens(vendaDesanexada.getIdVenda()).orElse(vendaDesanexada);
            ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
            if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) return;
            processarEmissao(venda, configLoja, "1", true);
            log.info("✅ Nota ID {} transmitida e autorizada com sucesso na SEFAZ.", venda.getIdVenda());
        } catch (Exception e) {
            log.error("❌ Falha na transmissão automática da venda {}: {}", vendaDesanexada.getIdVenda(), e.getMessage());
        }
    }

    private boolean isValidGTIN(String gtin) {
        if (gtin == null || !gtin.matches("\\d+")) return false;
        if (gtin.startsWith("2")) return false;

        int sum = 0;
        int length = gtin.length();
        for (int i = 0; i < length - 1; i++) {
            int num = Character.getNumericValue(gtin.charAt(i));
            if (length % 2 == 0) {
                sum += (i % 2 == 0) ? num * 3 : num;
            } else {
                sum += (i % 2 != 0) ? num * 3 : num;
            }
        }
        int checksum = (10 - (sum % 10)) % 10;
        return checksum == Character.getNumericValue(gtin.charAt(length - 1));
    }
}