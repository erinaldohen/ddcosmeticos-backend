package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoEmissao;
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

    /**
     * Consulta o status do serviço na SEFAZ (SVRS para PE)
     */
    public String consultarStatusSefaz() throws Exception {
        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) return "SIMULACAO";

        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
        TRetConsStatServ retorno = Nfe.statusServico(configNfe, DocumentoEnum.NFCE);
        return "Status: " + retorno.getCStat() + " - " + retorno.getXMotivo();
    }

    /**
     * Ponto de entrada para emissão da NFC-e
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NfceResponseDTO emitirNfce(Venda venda) {
        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();

        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            return simularEmissaoEmDev(venda, configLoja);
        }
        try {
            return processarEmissao(venda, configLoja, TipoEmissao.NORMAL);
        } catch (Exception e) {
            log.error("Falha na emissão NFC-e: {}", e.getMessage());
            throw new ValidationException("Erro na emissão NFC-e: " + e.getMessage());
        }
    }

    private NfceResponseDTO processarEmissao(Venda venda, ConfiguracaoLoja configLoja, TipoEmissao tipoEmissao) throws Exception {
        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
        String modelo = "65";

        // Uso da lógica centralizada na entidade para evitar erro de símbolo
        String serie = String.valueOf(configLoja.isProducao() ?
                configLoja.getFiscal().getSerieProducao() : configLoja.getFiscal().getSerieHomologacao());

        String nNF = String.valueOf(venda.getIdVenda() + 1000);
        String cnf = String.format("%08d", new Random().nextInt(99999999));
        String cnpjEmitente = configLoja.getCnpjLimpo();

        String chaveSemDigito = gerarChaveAcesso(configNfe.getEstado().getCodigoUF(), LocalDateTime.now(), cnpjEmitente, modelo, serie, nNF, tipoEmissao.getCodigo(), cnf);
        String dv = calcularDV(chaveSemDigito);
        String chaveAcesso = chaveSemDigito + dv;

        // Montagem do Objeto NFe
        TNFe nfe = new TNFe();
        TNFe.InfNFe infNFe = new TNFe.InfNFe();
        infNFe.setId("NFe" + chaveAcesso);
        infNFe.setVersao("4.00");
        infNFe.setIde(montarIde(configNfe, cnf, nNF, dv, modelo, serie, tipoEmissao.getCodigo()));
        infNFe.setEmit(montarEmit(configLoja));
        if (venda.getCliente() != null) infNFe.setDest(montarDest(venda.getCliente()));
        infNFe.getDet().addAll(montarDetalhes(venda.getItens(), configLoja));
        infNFe.setTotal(montarTotal(venda));
        infNFe.setTransp(montarTransp());
        infNFe.setPag(montarPag(venda.getPagamentos(), venda.getValorTotal(), venda.getTroco()));

        TNFe.InfNFe.InfAdic infAdic = new TNFe.InfNFe.InfAdic();
        infAdic.setInfCpl(tributacaoService.calcularTextoTransparencia(venda));
        infNFe.setInfAdic(infAdic);
        infNFe.setInfRespTec(montarRespTec());

        nfe.setInfNFe(infNFe);

        TEnviNFe enviNFe = new TEnviNFe();
        enviNFe.setVersao("4.00"); enviNFe.setIdLote("1"); enviNFe.setIndSinc("1"); enviNFe.getNFe().add(nfe);

        // 1º PASSO: ASSINAR O XML (Necessário para o QR Code)
        try {
            String xmlNaoAssinado = XmlNfeUtil.objectToXml(enviNFe);
            String xmlAssinado = br.com.swconsultoria.nfe.Assinar.assinaNfe(configNfe, xmlNaoAssinado, br.com.swconsultoria.nfe.dom.enuns.AssinaturaEnum.NFE);
            enviNFe = XmlNfeUtil.xmlToObject(xmlAssinado, TEnviNFe.class);
            nfe = enviNFe.getNFe().get(0);
        } catch (Exception e) {
            log.error("Erro fatal ao assinar XML: {}", e.getMessage());
            throw new ValidationException("Falha na Assinatura Digital do XML.");
        }

        // =========================================================
        // 2º PASSO: CONSTRUÇÃO QR CODE (REGRAS SVRS / PE)
        // =========================================================
        String cscIdRaw = configLoja.isProducao() ? configLoja.getFiscal().getCscIdProducao() : configLoja.getFiscal().getCscIdHomologacao();
        String cscTokenRaw = configLoja.isProducao() ? configLoja.getFiscal().getTokenProducao() : configLoja.getFiscal().getTokenHomologacao();

        if (cscIdRaw != null && !cscIdRaw.trim().isEmpty() && cscTokenRaw != null && !cscTokenRaw.trim().isEmpty()) {
            try {
                String tokenLimpo = cscTokenRaw.trim().replaceAll("\\s+", "");
                String cscIdFormatado = String.format("%06d", Integer.parseInt(cscIdRaw.trim().replaceAll("\\D", "")));
                String ambiente = configNfe.getAmbiente().getCodigo();

                // Pernambuco exige URLs específicas
                String urlQrCode = configLoja.isProducao()
                        ? "http://nfce.sefaz.pe.gov.br/nfce-web/consultarNFCe"
                        : "http://nfcehomolog.sefaz.pe.gov.br/nfce-web/consultarNFCe";

                String urlChave = configLoja.isProducao()
                        ? "http://nfce.sefaz.pe.gov.br/nfce/consulta"
                        : "http://nfcehomolog.sefaz.pe.gov.br/nfce/consulta";

                // Cálculo do Hash SHA-1
                String parametros = chaveAcesso + "|2|" + ambiente + "|" + cscIdFormatado;
                String hashInput = parametros + tokenLimpo;

                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
                byte[] hashResult = md.digest(hashInput.getBytes(StandardCharsets.UTF_8));
                StringBuilder hexFinal = new StringBuilder();
                for (byte b : hashResult) hexFinal.append(String.format("%02x", b));
                String hashFinalStr = hexFinal.toString().toUpperCase();

                String qrCodeCompleto = urlQrCode + "?p=" + parametros + "|" + hashFinalStr;

                TNFe.InfNFeSupl supl = new TNFe.InfNFeSupl();
                supl.setQrCode(qrCodeCompleto);
                supl.setUrlChave(urlChave);
                nfe.setInfNFeSupl(supl);

                // Recria o objeto final para garantir a integridade do XML
                TEnviNFe enviFinal = new TEnviNFe();
                enviFinal.setVersao("4.00"); enviFinal.setIdLote("1"); enviFinal.setIndSinc("1"); enviFinal.getNFe().add(nfe);
                String xmlFinal = XmlNfeUtil.objectToXml(enviFinal);
                enviNFe = XmlNfeUtil.xmlToObject(xmlFinal, TEnviNFe.class);

            } catch (Exception e) {
                log.error("Falha ao gerar QR Code: ", e);
            }
        }

        // =========================================================
        // 3º PASSO: ENVIAR PARA A SEFAZ
        // =========================================================
        TRetEnviNFe retorno = Nfe.enviarNfe(configNfe, enviNFe, DocumentoEnum.NFCE);

        if (retorno.getProtNFe() != null && "100".equals(retorno.getProtNFe().getInfProt().getCStat())) {
            venda.setStatusNfce(StatusFiscal.AUTORIZADA);
            venda.setChaveAcessoNfce(chaveAcesso);
            venda.setXmlNota(XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe()));
            vendaRepository.save(venda);
        } else {
            String erro = retorno.getProtNFe() != null ? retorno.getProtNFe().getInfProt().getXMotivo() : retorno.getXMotivo();
            throw new ValidationException("Sefaz: " + erro);
        }

        String linkFront = (nfe.getInfNFeSupl() != null) ? nfe.getInfNFeSupl().getQrCode() : "";
        return new NfceResponseDTO(venda.getIdVenda(), "100", "Sucesso", chaveAcesso, "", venda.getXmlNota(), linkFront);
    }

    private ConfiguracoesNfe iniciarConfiguracoesNfe(ConfiguracaoLoja loja) throws Exception {
        Certificado cert = CertificadoService.certificadoPfxBytes(loja.getFiscal().getArquivoCertificado(), loja.getFiscal().getSenhaCert());
        AmbienteEnum ambiente = loja.isProducao() ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO;
        return ConfiguracoesNfe.criarConfiguracoes(EstadosEnum.valueOf(loja.getEndereco().getUf()), ambiente, cert, "schemas");
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itensVenda, ConfiguracaoLoja configLoja) {
        List<TNFe.InfNFe.Det> detalhes = new ArrayList<>();
        ObjectFactory factory = new ObjectFactory();
        int i = 1;

        for (ItemVenda item : itensVenda) {
            TNFe.InfNFe.Det det = new TNFe.InfNFe.Det();
            det.setNItem(String.valueOf(i));

            TNFe.InfNFe.Det.Prod prod = new TNFe.InfNFe.Det.Prod();
            prod.setCProd(item.getProduto().getId().toString());
            prod.setCEAN("SEM GTIN");

            // Mensagem obrigatória de homologação
            if (i == 1 && !configLoja.isProducao()) {
                prod.setXProd("NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL");
            } else {
                prod.setXProd(item.getProduto().getDescricao());
            }

            prod.setNCM(item.getProduto().getNcm() != null ? item.getProduto().getNcm().replaceAll("\\D", "") : "00000000");
            if (item.getProduto().getCest() != null && !item.getProduto().getCest().isBlank())
                prod.setCEST(item.getProduto().getCest().replaceAll("\\D", ""));

            // Define o CFOP baseado na configuração fiscal da loja
            prod.setCFOP(configLoja.getFiscal().getNaturezaPadrao().contains("5.405") ? "5405" : "5102");

            String u = item.getProduto().getUnidade();
            prod.setUCom(u != null && u.length() <= 6 ? u : "UN");
            prod.setQCom(item.getQuantidade().setScale(4, RoundingMode.HALF_UP).toString());
            prod.setVUnCom(item.getPrecoUnitario().setScale(2, RoundingMode.HALF_UP).toString());
            prod.setVProd(item.getPrecoUnitario().multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP).toString());
            prod.setCEANTrib("SEM GTIN");
            prod.setUTrib(prod.getUCom());
            prod.setQTrib(prod.getQCom());
            prod.setVUnTrib(prod.getVUnCom());
            prod.setIndTot("1");
            det.setProd(prod);

            // Tributação Simples Nacional (CSOSN 102/400)
            TNFe.InfNFe.Det.Imposto imp = new TNFe.InfNFe.Det.Imposto();
            TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();
            TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 s102 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
            s102.setOrig("0");
            s102.setCSOSN("102");
            icms.setICMSSN102(s102);
            imp.getContent().add(factory.createTNFeInfNFeDetImpostoICMS(icms));
            det.setImposto(imp);

            detalhes.add(det);
            i++;
        }
        return detalhes;
    }

    private TNFe.InfNFe.Total montarTotal(Venda venda) {
        TNFe.InfNFe.Total t = new TNFe.InfNFe.Total();
        TNFe.InfNFe.Total.ICMSTot icms = new TNFe.InfNFe.Total.ICMSTot();
        String v = venda.getValorTotal().setScale(2, RoundingMode.HALF_UP).toString();
        icms.setVBC("0.00"); icms.setVICMS("0.00"); icms.setVProd(v); icms.setVNF(v);
        icms.setVICMSDeson("0.00"); icms.setVFCP("0.00"); icms.setVBCST("0.00"); icms.setVST("0.00");
        icms.setVFCPST("0.00"); icms.setVFCPSTRet("0.00"); icms.setVFrete("0.00"); icms.setVSeg("0.00");
        icms.setVDesc("0.00"); icms.setVII("0.00"); icms.setVIPI("0.00"); icms.setVIPIDevol("0.00");
        icms.setVPIS("0.00"); icms.setVCOFINS("0.00"); icms.setVOutro("0.00");
        t.setICMSTot(icms);
        return t;
    }

    private TNFe.InfNFe.Pag montarPag(List<PagamentoVenda> pags, BigDecimal tot, BigDecimal troco) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
        det.setTPag("01"); // Dinheiro por padrão (ajustar conforme necessidade)
        det.setVPag(tot.setScale(2, RoundingMode.HALF_UP).toString());
        pag.getDetPag().add(det);
        pag.setVTroco(troco != null ? troco.setScale(2, RoundingMode.HALF_UP).toString() : "0.00");
        return pag;
    }

    private TNFe.InfNFe.Ide montarIde(ConfiguracoesNfe cfg, String cnf, String nNF, String dv, String mod, String ser, String tp) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(cfg.getEstado().getCodigoUF()); ide.setCNF(cnf); ide.setNatOp("VENDA CONSUMIDOR");
        ide.setMod(mod); ide.setSerie(ser); ide.setNNF(nNF); ide.setDhEmi(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setTpNF("1"); ide.setIdDest("1"); ide.setCMunFG("2611606"); ide.setTpImp("4");
        ide.setTpEmis(tp); ide.setCDV(dv); ide.setTpAmb(cfg.getAmbiente().getCodigo());
        ide.setFinNFe("1"); ide.setIndFinal("1"); ide.setIndPres("1"); ide.setProcEmi("0"); ide.setVerProc("1.0");
        return ide;
    }

    private TNFe.InfNFe.Emit montarEmit(ConfiguracaoLoja cfg) {
        TNFe.InfNFe.Emit e = new TNFe.InfNFe.Emit();
        e.setCNPJ(cfg.getCnpjLimpo());
        e.setXNome(cfg.getLoja().getRazaoSocial());
        e.setIE(cfg.getLoja().getIe().replaceAll("\\D", ""));
        e.setCRT("1");
        TEnderEmi end = new TEnderEmi();
        end.setXLgr(cfg.getEndereco().getLogradouro()); end.setNro(cfg.getEndereco().getNumero());
        end.setXBairro(cfg.getEndereco().getBairro()); end.setCMun("2611606"); end.setXMun("RECIFE");
        end.setUF(TUfEmi.valueOf(cfg.getEndereco().getUf())); end.setCEP(cfg.getEndereco().getCep().replaceAll("\\D", ""));
        end.setCPais("1058"); end.setXPais("BRASIL"); e.setEnderEmit(end);
        return e;
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

    private TInfRespTec montarRespTec() {
        TInfRespTec respTec = new TInfRespTec();
        respTec.setCNPJ("57648950000144");
        respTec.setXContato("Suporte Tecnico");
        respTec.setEmail("suporte@empresa.com.br");
        respTec.setFone("81999999999");
        return respTec;
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

    private NfceResponseDTO simularEmissaoEmDev(Venda v, ConfiguracaoLoja c) {
        return new NfceResponseDTO(v.getIdVenda(), "100", "Simulado", "35230900000000000000650010000000011000000001", "123", "", "");
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transmitirNotaContingencia(Venda venda) {
        try {
            log.info("📡 Tentando transmitir nota pendente/contingência ID: {}", venda.getIdVenda());
            ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
            // Tenta processar a emissão novamente
            processarEmissao(venda, configLoja, TipoEmissao.NORMAL);
        } catch (Exception e) {
            log.error("❌ Falha na transmissão automática da venda {}: {}", venda.getIdVenda(), e.getMessage());
        }
    }
}