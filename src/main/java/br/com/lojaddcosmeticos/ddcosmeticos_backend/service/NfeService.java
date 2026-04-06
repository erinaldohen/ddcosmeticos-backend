package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PagamentoVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.*;
import br.com.swconsultoria.nfe.schema_4.consStatServ.TRetConsStatServ;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import br.com.swconsultoria.nfe.schema_4.enviNFe.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
public class NfeService {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private ConfiguracaoLojaService configuracaoLojaService;

    public String consultarStatusSefaz() throws Exception {
        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) return "SIMULACAO";
        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
        TRetConsStatServ retorno = Nfe.statusServico(configNfe, DocumentoEnum.NFE);
        return "Status: " + retorno.getCStat();
    }

    @Transactional
    public NfceResponseDTO emitirNfeModelo55(Long idVenda) {
        Venda venda = vendaRepository.findById(idVenda).orElseThrow(() -> new ValidationException("Venda não encontrada."));

        // 🚨 TRAVA DE SEGURANÇA SEFAZ
        boolean isFiado = venda.getPagamentos() != null && venda.getPagamentos().stream()
                .anyMatch(p -> p.getFormaPagamento() != null &&
                        (p.getFormaPagamento().name().equalsIgnoreCase("FIADO") || p.getFormaPagamento().name().equalsIgnoreCase("CREDIARIO")));

        if (venda.getCliente() == null || venda.getCliente().getDocumento() == null || venda.getCliente().getDocumento().isBlank()) {
            throw new ValidationException("SEFAZ Rejeita: Para emitir NF-e (Modelo 55), é obrigatório informar um Cliente com CPF/CNPJ válido.");
        }

        ConfiguracaoLoja configLoja = configuracaoLojaService.buscarConfiguracao();
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) return simularNfe(venda);

        try {
            ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
            String modelo = "55";
            String serie = "1";
            String nNF = String.valueOf(venda.getIdVenda());
            String cnf = String.format("%08d", new Random().nextInt(99999999));
            String cnpjEmit = configLoja.getLoja().getCnpj().replaceAll("\\D", "");

            String chaveSemDigito = gerarChaveAcesso(configNfe.getEstado().getCodigoUF(), LocalDateTime.now(), cnpjEmit, modelo, serie, nNF, "1", cnf);
            String dv = calcularDV(chaveSemDigito);
            String chaveAcesso = chaveSemDigito + dv;

            TNFe nfe = new TNFe();
            TNFe.InfNFe infNFe = new TNFe.InfNFe();
            infNFe.setId("NFe" + chaveAcesso);
            infNFe.setVersao("4.00");
            infNFe.setIde(montarIde(configNfe, cnf, nNF, dv, modelo, serie));
            infNFe.setEmit(montarEmit(configLoja));
            infNFe.setDest(montarDest(venda.getCliente()));
            infNFe.getDet().addAll(montarDetalhes(venda.getItens(), configLoja));
            infNFe.setTotal(montarTotal(venda));
            infNFe.setTransp(montarTransp());

            // CORREÇÃO DOS PAGAMENTOS APLICADA AQUI
            infNFe.setPag(montarPag(venda.getPagamentos(), venda.getValorTotal()));

            // 🚨 EXIGÊNCIA DA SEFAZ: SE FOR FIADO NA NFE 55, EXIGE O GRUPO DE COBRANÇA <cobr>
            if (isFiado) {
                TNFe.InfNFe.Cobr cobr = new TNFe.InfNFe.Cobr();
                TNFe.InfNFe.Cobr.Fat fat = new TNFe.InfNFe.Cobr.Fat();
                fat.setNFat(String.valueOf(venda.getIdVenda()));
                fat.setVOrig(venda.getValorTotal().setScale(2, RoundingMode.HALF_UP).toString());
                fat.setVDesc("0.00");
                fat.setVLiq(venda.getValorTotal().setScale(2, RoundingMode.HALF_UP).toString());
                cobr.setFat(fat);

                TNFe.InfNFe.Cobr.Dup dup = new TNFe.InfNFe.Cobr.Dup();
                dup.setNDup("001");
                dup.setDVenc(XmlNfeUtil.dataNfe(LocalDateTime.now().plusDays(30))); // Padronizado 30 dias na simplificada
                dup.setVDup(venda.getValorTotal().setScale(2, RoundingMode.HALF_UP).toString());
                cobr.getDup().add(dup);

                infNFe.setCobr(cobr);
            }

            infNFe.setInfRespTec(montarRespTec());

            nfe.setInfNFe(infNFe);

            TEnviNFe enviNFe = new TEnviNFe();
            enviNFe.setVersao("4.00"); enviNFe.setIdLote("1"); enviNFe.setIndSinc("1"); enviNFe.getNFe().add(nfe);

            try {
                String xmlNaoAssinado = XmlNfeUtil.objectToXml(enviNFe);
                String xmlAssinado = br.com.swconsultoria.nfe.Assinar.assinaNfe(configNfe, xmlNaoAssinado, AssinaturaEnum.NFE);
                enviNFe = XmlNfeUtil.xmlToObject(xmlAssinado, TEnviNFe.class);
            } catch (Exception e) {
                log.error("Erro ao assinar XML da NF-e: {}", e.getMessage());
                throw new ValidationException("Falha na Assinatura Digital.");
            }

            TRetEnviNFe retorno = Nfe.enviarNfe(configNfe, enviNFe, DocumentoEnum.NFE);

            if (retorno.getProtNFe() != null && "100".equals(retorno.getProtNFe().getInfProt().getCStat())) {
                venda.setStatusNfce(StatusFiscal.AUTORIZADA);
                venda.setChaveAcessoNfce(chaveAcesso);
                venda.setXmlNota(XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe()));
                vendaRepository.save(venda);
                return new NfceResponseDTO(venda.getIdVenda(), "100", "Autorizada", chaveAcesso, "", venda.getXmlNota(), null);
            } else {
                String erro = retorno.getProtNFe() != null ? retorno.getProtNFe().getInfProt().getXMotivo() : retorno.getXMotivo();
                throw new ValidationException("Erro SEFAZ: " + erro);
            }
        } catch (Exception e) {
            throw new ValidationException("Erro: " + e.getMessage());
        }
    }

    private TInfRespTec montarRespTec() {
        TInfRespTec resp = new TInfRespTec();
        resp.setCNPJ("57648950000144");
        resp.setXContato("Suporte Tecnico");
        resp.setEmail("suporte@empresa.com.br");
        resp.setFone("81999999999");
        return resp;
    }

    private ConfiguracoesNfe iniciarConfiguracoesNfe(ConfiguracaoLoja loja) throws Exception {
        Certificado cert = CertificadoService.certificadoPfxBytes(loja.getFiscal().getArquivoCertificado(), loja.getFiscal().getSenhaCert());
        AmbienteEnum amb = "PRODUCAO".equalsIgnoreCase(loja.getFiscal().getAmbiente()) ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO;
        return ConfiguracoesNfe.criarConfiguracoes(EstadosEnum.valueOf(loja.getEndereco().getUf()), amb, cert, "schemas");
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itens, ConfiguracaoLoja config) {
        List<TNFe.InfNFe.Det> list = new ArrayList<>();
        int i = 1;
        boolean isHomolog = !"PRODUCAO".equalsIgnoreCase(config.getFiscal().getAmbiente());
        for (ItemVenda item : itens) {
            TNFe.InfNFe.Det d = new TNFe.InfNFe.Det();
            d.setNItem(String.valueOf(i));
            TNFe.InfNFe.Det.Prod p = new TNFe.InfNFe.Det.Prod();
            p.setCProd(item.getProduto().getId().toString());
            p.setCEAN("SEM GTIN");

            if (i == 1 && isHomolog) {
                p.setXProd("NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL");
            } else {
                p.setXProd(item.getDescricaoProduto() != null ? item.getDescricaoProduto() : item.getProduto().getDescricao());
            }

            p.setNCM(item.getProduto().getNcm() != null ? item.getProduto().getNcm().replaceAll("\\D", "") : "33049990");
            String u = item.getProduto().getUnidade();
            p.setUCom(u != null && u.length() <= 6 ? u : "UN");
            p.setQCom(item.getQuantidade().setScale(4, RoundingMode.HALF_UP).toString());
            p.setVUnCom(item.getPrecoUnitario().setScale(2, RoundingMode.HALF_UP).toString());
            p.setVProd(item.getPrecoUnitario().multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP).toString());
            p.setCFOP("5102"); p.setIndTot("1");
            d.setProd(p);

            TNFe.InfNFe.Det.Imposto imp = new TNFe.InfNFe.Det.Imposto();
            TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();
            TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 s102 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
            s102.setOrig("0"); s102.setCSOSN("102"); icms.setICMSSN102(s102);
            imp.getContent().add(new ObjectFactory().createTNFeInfNFeDetImpostoICMS(icms));
            d.setImposto(imp);
            list.add(d);
            i++;
        }
        return list;
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

    // 🚨 REGRA SEFAZ: MAPEAMENTO EXATO DOS TIPOS DE PAGAMENTO E A PRAZO (indPag=1)
    private TNFe.InfNFe.Pag montarPag(List<PagamentoVenda> pagamentos, BigDecimal tot) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();

        if (pagamentos == null || pagamentos.isEmpty()) {
            TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
            det.setTPag("01");
            det.setVPag(tot.setScale(2, RoundingMode.HALF_UP).toString());
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
                            tPag = "05"; // 05 = Crédito Loja
                            det.setIndPag("1"); // 1 = A Prazo
                            break;
                        case "DINHEIRO": tPag = "01"; break;
                    }
                }

                det.setTPag(tPag);
                det.setVPag(p.getValor().setScale(2, RoundingMode.HALF_UP).toString());
                pag.getDetPag().add(det);
            }
        }
        return pag;
    }

    private TNFe.InfNFe.Ide montarIde(ConfiguracoesNfe cfg, String cnf, String nNF, String dv, String mod, String ser) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(cfg.getEstado().getCodigoUF()); ide.setCNF(cnf); ide.setNatOp("VENDA DE MERCADORIAS");
        ide.setMod(mod); ide.setSerie(ser); ide.setNNF(nNF); ide.setDhEmi(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setTpNF("1"); ide.setIdDest("1"); ide.setCMunFG("2611606"); ide.setTpImp("1");
        ide.setTpEmis("1"); ide.setCDV(dv); ide.setTpAmb(cfg.getAmbiente().getCodigo());
        ide.setFinNFe("1"); ide.setIndFinal("1"); ide.setIndPres("1"); ide.setProcEmi("0"); ide.setVerProc("1.0");
        return ide;
    }

    private TNFe.InfNFe.Emit montarEmit(ConfiguracaoLoja cfg) {
        TNFe.InfNFe.Emit e = new TNFe.InfNFe.Emit();
        e.setCNPJ(cfg.getLoja().getCnpj().replaceAll("\\D", ""));
        e.setXNome(cfg.getLoja().getRazaoSocial()); e.setIE(cfg.getLoja().getIe().replaceAll("\\D", ""));
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
        d.setIndIEDest("9");
        TEndereco end = new TEndereco();
        end.setXLgr(c.getEndereco() != null ? c.getEndereco() : "Nao Informado");
        end.setNro("S/N"); end.setXBairro("CENTRO"); end.setCMun("2611606"); end.setXMun("RECIFE");
        end.setUF(TUf.PE); end.setCEP("50000000"); end.setCPais("1058"); end.setXPais("BRASIL");
        d.setEnderDest(end);
        return d;
    }

    private TNFe.InfNFe.Transp montarTransp() {
        TNFe.InfNFe.Transp t = new TNFe.InfNFe.Transp();
        t.setModFrete("9");
        return t;
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

    private NfceResponseDTO simularNfe(Venda v) {
        return new NfceResponseDTO(v.getIdVenda(), "100", "Simulado", "35230900000000000000550010000000011000000001", "123", "", "");
    }
}