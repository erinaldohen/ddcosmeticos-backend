package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
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
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import br.com.swconsultoria.nfe.schema_4.enviNFe.*;

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
public class NfceService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ConfiguracaoLojaRepository configuracaoLojaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NfceResponseDTO emitirNfce(Venda venda) {
        // 1. Carregar Configurações
        ConfiguracaoLoja configLoja = configuracaoLojaRepository.findById(1L)
                .orElseThrow(() -> new ValidationException("Loja não configurada."));

        // Validação de segurança para dev
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            return simularEmissaoEmDev(venda, configLoja);
        }

        try {
            // 2. Inicializar Configuração da Lib
            ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);

            // 3. Dados da Nota
            String modelo = "65"; // NFC-e
            String serie = "1";
            String nNF = String.valueOf(venda.getIdVenda() + 1000);
            String cnf = String.format("%08d", new Random().nextInt(99999999));

            String cnpjEmitente = "00000000000000";
            if (configLoja.getLoja() != null && configLoja.getLoja().getCnpj() != null) {
                cnpjEmitente = configLoja.getLoja().getCnpj().replaceAll("\\D", "");
            }

            // 4. Montar Chave
            String chaveSemDigito = gerarChaveAcesso(
                    configNfe.getEstado().getCodigoUF(),
                    LocalDateTime.now(),
                    cnpjEmitente,
                    modelo,
                    serie,
                    nNF,
                    "1",
                    cnf
            );
            String dv = calcularDV(chaveSemDigito);
            String chaveAcesso = chaveSemDigito + dv;

            // 5. Preencher XML
            TNFe nfe = new TNFe();
            TNFe.InfNFe infNFe = new TNFe.InfNFe();
            infNFe.setId("NFe" + chaveAcesso);
            infNFe.setVersao("4.00");

            infNFe.setIde(montarIde(configNfe, cnf, nNF, dv, modelo, serie));
            infNFe.setEmit(montarEmit(configLoja));

            if (venda.getCliente() != null) {
                infNFe.setDest(montarDest(venda.getCliente()));
            }

            infNFe.getDet().addAll(montarDetalhes(venda.getItens()));
            infNFe.setTotal(montarTotal(venda));
            infNFe.setTransp(montarTransp());
            infNFe.setPag(montarPag(venda.getPagamentos(), venda.getValorTotal()));

            nfe.setInfNFe(infNFe);

            // 6. Enviar
            TEnviNFe enviNFe = new TEnviNFe();
            enviNFe.setVersao("4.00");
            enviNFe.setIdLote("1");
            enviNFe.setIndSinc("1");
            enviNFe.getNFe().add(nfe);

            TRetEnviNFe retorno = Nfe.enviarNfe(configNfe, enviNFe, DocumentoEnum.NFCE);

            // 7. Validar Retorno
            String status = retorno.getProtNFe().getInfProt().getCStat();
            String motivo = retorno.getProtNFe().getInfProt().getXMotivo();

            // Tratamento seguro para nulo
            String protocolo = "";
            if (retorno.getProtNFe().getInfProt().getNProt() != null) {
                protocolo = retorno.getProtNFe().getInfProt().getNProt();
            }

            String xmlFinal = XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe());

            // Lógica do CSC para QR Code
            String cscId;
            if ("PRODUCAO".equalsIgnoreCase(configLoja.getFiscal().getAmbiente())) {
                cscId = configLoja.getFiscal().getCscIdProducao();
            } else {
                cscId = configLoja.getFiscal().getCscIdHomologacao();
            }

            String urlQrCode = "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx?p=" + chaveAcesso + "|2|1|1|" + cscId;

            if (!status.equals("100")) {
                throw new ValidationException("Rejeição SEFAZ: " + status + " - " + motivo);
            }

            // CORREÇÃO: Ordem dos parâmetros ajustada para o seu DTO existente
            return new NfceResponseDTO(
                    venda.getIdVenda(), // 1. ID (Long)
                    status,             // 2. Status
                    motivo,             // 3. Motivo
                    chaveAcesso,        // 4. Chave Acesso (String) - ERRO ESTAVA AQUI
                    protocolo,          // 5. Protocolo
                    xmlFinal,           // 6. XML
                    urlQrCode           // 7. URL QR Code
            );

        } catch (Exception e) {
            e.printStackTrace();
            throw new ValidationException("Erro na emissão NFC-e: " + e.getMessage());
        }
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

        // CORREÇÃO: Ordem dos parâmetros ajustada
        return new NfceResponseDTO(
                venda.getIdVenda(),                 // 1. ID (Long)
                "100",                              // 2. Status
                "EMISSAO SIMULADA (SEM CERTIFICADO)", // 3. Motivo
                chaveFake,                          // 4. Chave Fake (String) - ERRO ESTAVA AQUI
                "1234567890",                       // 5. Protocolo
                "<xml>Simulacao</xml>",             // 6. XML
                "https://www.sefaz.rs.gov.br/NFCE/NFCE-COM.aspx" // 7. URL
        );
    }

    // --- Montadores XML ---

    private TNFe.InfNFe.Ide montarIde(ConfiguracoesNfe config, String cnf, String nNF, String dv, String modelo, String serie) {
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
        ide.setCMunFG("2611606");
        ide.setTpImp("4");
        ide.setTpEmis("1");
        ide.setCDV(dv);
        ide.setTpAmb(config.getAmbiente().getCodigo());
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("1.0");
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
        return dest;
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itens) {
        return Collections.emptyList();
    }

    private TNFe.InfNFe.Total montarTotal(Venda venda) {
        TNFe.InfNFe.Total total = new TNFe.InfNFe.Total();
        TNFe.InfNFe.Total.ICMSTot icmsTot = new TNFe.InfNFe.Total.ICMSTot();
        icmsTot.setVBC("0.00"); icmsTot.setVICMS("0.00"); icmsTot.setVProd("0.00");
        icmsTot.setVNF("0.00"); icmsTot.setVDesc("0.00"); icmsTot.setVOutro("0.00");
        icmsTot.setVIPI("0.00"); icmsTot.setVPIS("0.00"); icmsTot.setVCOFINS("0.00");
        icmsTot.setVFrete("0.00"); icmsTot.setVSeg("0.00");
        total.setICMSTot(icmsTot);
        return total;
    }

    private TNFe.InfNFe.Transp montarTransp() {
        TNFe.InfNFe.Transp t = new TNFe.InfNFe.Transp();
        t.setModFrete("9");
        return t;
    }

    private TNFe.InfNFe.Pag montarPag(List<PagamentoVenda> pags, BigDecimal total) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
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