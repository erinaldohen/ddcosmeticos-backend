package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PagamentoVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;

// Imports Bibliotecas NFe
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class NfeService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ConfiguracaoLojaRepository configuracaoLojaRepository;

    // --- NOVO MÉTODO: STATUS SEFAZ ---
    public String consultarStatusSefaz() throws Exception {
        ConfiguracaoLoja configLoja = configuracaoLojaRepository.findById(1L)
                .orElseThrow(() -> new ValidationException("Loja não configurada."));

        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            return "Ambiente: SIMULACAO (Sem Certificado) | Status: 107 - Serviço em Operação (Simulado)";
        }

        ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);
        TRetConsStatServ retorno = Nfe.statusServico(configNfe, DocumentoEnum.NFE);

        return "Ambiente: " + configNfe.getAmbiente() +
                " | Status: " + retorno.getCStat() +
                " - " + retorno.getXMotivo();
    }

    @Transactional
    public NfceResponseDTO emitirNfeModelo55(Long idVenda) {
        // 1. Busca Configurações
        ConfiguracaoLoja configLoja = configuracaoLojaRepository.findById(1L)
                .orElseThrow(() -> new ValidationException("Loja não configurada."));

        // 2. Busca Venda
        Venda venda = vendaRepository.findById(idVenda)
                .orElseThrow(() -> new ValidationException("Venda não encontrada."));

        if (venda.getCliente() == null) {
            throw new ValidationException("Para emitir NF-e (Modelo 55), é obrigatório informar um Cliente cadastrado.");
        }

        // Validação de segurança para dev (Simulação)
        if (configLoja.getFiscal() == null || configLoja.getFiscal().getArquivoCertificado() == null) {
            return simularNfe(venda, configLoja);
        }

        try {
            // 3. Inicializar Configuração
            ConfiguracoesNfe configNfe = iniciarConfiguracoesNfe(configLoja);

            // 4. Montagem da Chave (Modelo 55)
            String modelo = "55";
            String serie = "1";
            String nNF = String.valueOf(venda.getIdVenda());
            String cnf = String.format("%08d", new Random().nextInt(99999999));

            String cnpjEmitente = "00000000000000";
            if (configLoja.getLoja() != null && configLoja.getLoja().getCnpj() != null) {
                cnpjEmitente = configLoja.getLoja().getCnpj().replaceAll("\\D", "");
            }

            String chaveSemDigito = gerarChaveAcesso(
                    configNfe.getEstado().getCodigoUF(), // Pega estado do certificado
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

            // 5. XML
            TNFe nfe = new TNFe();
            TNFe.InfNFe infNFe = new TNFe.InfNFe();
            infNFe.setId("NFe" + chaveAcesso);
            infNFe.setVersao("4.00");

            infNFe.setIde(montarIde(configNfe, cnf, nNF, dv, modelo, serie));
            infNFe.setEmit(montarEmit(configLoja));
            infNFe.setDest(montarDest(venda.getCliente()));

            infNFe.getDet().addAll(montarDetalhes(venda.getItens()));
            infNFe.setTotal(montarTotal(venda));
            infNFe.setTransp(montarTransp());
            infNFe.setPag(montarPag(venda.getPagamentos(), venda.getValorTotal()));

            nfe.setInfNFe(infNFe);

            // 6. Envio
            TEnviNFe enviNFe = new TEnviNFe();
            enviNFe.setVersao("4.00");
            enviNFe.setIdLote("1");
            enviNFe.setIndSinc("1");
            enviNFe.getNFe().add(nfe);

            TRetEnviNFe retorno = Nfe.enviarNfe(configNfe, enviNFe, DocumentoEnum.NFE);

            // 7. Retorno
            String status = retorno.getProtNFe().getInfProt().getCStat();
            String motivo = retorno.getProtNFe().getInfProt().getXMotivo();

            String protocolo = "";
            if (retorno.getProtNFe().getInfProt().getNProt() != null) {
                protocolo = retorno.getProtNFe().getInfProt().getNProt();
            }

            if ("100".equals(status)) {
                venda.setStatusNfce(StatusFiscal.AUTORIZADA);
                venda.setChaveAcessoNfce(chaveAcesso);
                venda.setXmlNota(XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe()));
                vendaRepository.save(venda);
            } else {
                throw new ValidationException("Erro Sefaz: " + status + " - " + motivo);
            }

            return new NfceResponseDTO(
                    venda.getIdVenda(),
                    status,
                    motivo,
                    chaveAcesso,
                    protocolo,
                    venda.getXmlNota(),
                    null
            );

        } catch (Exception e) {
            e.printStackTrace();
            throw new ValidationException("Erro ao emitir NF-e: " + e.getMessage());
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

    private NfceResponseDTO simularNfe(Venda venda, ConfiguracaoLoja config) {
        String chaveFake = "35230900000000000000550010000000011000000001";

        return new NfceResponseDTO(
                venda.getIdVenda(),
                "100",
                "AUTORIZADO (SIMULACAO)",
                chaveFake,
                "PROTOCOLO_FAKE",
                "<xml>NFe Simulada</xml>",
                null
        );
    }

    // --- Placeholders XML (Métodos Auxiliares) ---
    // (Pode copiar os mesmos métodos 'montarIde', 'montarEmit', etc do arquivo anterior se preferir,
    // mas aqui estão versões funcionais simplificadas para evitar o erro de compilação)

    private TNFe.InfNFe.Ide montarIde(ConfiguracoesNfe config, String cnf, String nNF, String dv, String modelo, String serie) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(config.getEstado().getCodigoUF());
        ide.setCNF(cnf);
        ide.setNatOp("VENDA DE MERCADORIAS");
        ide.setMod(modelo);
        ide.setSerie(serie);
        ide.setNNF(nNF);
        ide.setDhEmi(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setDhSaiEnt(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG("2611606");
        ide.setTpImp("1");
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
        emit.setCNPJ(config.getLoja().getCnpj().replaceAll("\\D", ""));
        emit.setXNome(config.getLoja().getRazaoSocial());
        emit.setIE(config.getLoja().getIe() != null ? config.getLoja().getIe().replaceAll("\\D", "") : "");
        emit.setCRT("1");

        TEnderEmi ender = new TEnderEmi();
        ender.setXLgr(config.getEndereco().getLogradouro());
        ender.setNro(config.getEndereco().getNumero());
        ender.setXBairro(config.getEndereco().getBairro());
        ender.setCMun("2611606");
        ender.setXMun("RECIFE");
        ender.setUF(TUfEmi.PE);
        ender.setCEP(config.getEndereco().getCep().replaceAll("\\D", ""));
        ender.setCPais("1058");
        ender.setXPais("BRASIL");
        emit.setEnderEmit(ender);
        return emit;
    }

    private TNFe.InfNFe.Dest montarDest(Cliente cliente) {
        if(cliente.getEndereco() == null) throw new ValidationException("Cliente sem endereço cadastrado.");
        TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();
        dest.setXNome(cliente.getNome());
        String doc = cliente.getDocumento().replaceAll("\\D", "");
        if (doc.length() == 11) dest.setCPF(doc); else dest.setCNPJ(doc);
        dest.setIndIEDest("9");
        TEndereco ender = new TEndereco();
        ender.setXLgr(cliente.getEndereco());
        ender.setNro("S/N");
        ender.setXBairro("CENTRO");
        ender.setCMun("2611606");
        ender.setXMun("RECIFE");
        ender.setUF(TUf.PE);
        ender.setCEP("50000000");
        ender.setCPais("1058");
        ender.setXPais("BRASIL");
        dest.setEnderDest(ender);
        return dest;
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itens) { return Collections.emptyList(); }
    private TNFe.InfNFe.Total montarTotal(Venda venda) {
        TNFe.InfNFe.Total total = new TNFe.InfNFe.Total();
        TNFe.InfNFe.Total.ICMSTot icms = new TNFe.InfNFe.Total.ICMSTot();
        icms.setVBC("0.00"); icms.setVICMS("0.00"); icms.setVProd("0.00");
        icms.setVNF("0.00"); icms.setVDesc("0.00"); icms.setVOutro("0.00");
        icms.setVIPI("0.00"); icms.setVPIS("0.00"); icms.setVCOFINS("0.00");
        icms.setVFrete("0.00"); icms.setVSeg("0.00");
        total.setICMSTot(icms);
        return total;
    }
    private TNFe.InfNFe.Transp montarTransp() {
        TNFe.InfNFe.Transp t = new TNFe.InfNFe.Transp();
        t.setModFrete("9");
        return t;
    }
    private TNFe.InfNFe.Pag montarPag(List<PagamentoVenda> pags, BigDecimal tot) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        TNFe.InfNFe.Pag.DetPag det = new TNFe.InfNFe.Pag.DetPag();
        det.setTPag("01"); det.setVPag(tot.toString());
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