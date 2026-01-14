package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ClienteRepository; // Import necessário
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;

// Imports da Biblioteca Java-NFe
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;

// Imports dos Schemas
import br.com.swconsultoria.nfe.schema_4.enviNFe.ObjectFactory;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TEnviNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TRetEnviNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TEnderEmi;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TEndereco;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TUf;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TUfEmi;

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
public class NfeService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ClienteRepository clienteRepository; // Repositório para buscar o Cliente

    @Autowired
    private ConfiguracoesNfe configuracoesNfe;

    @Transactional
    public NfceResponseDTO emitirNfeModelo55(Long idVenda) {
        // 1. Busca Venda
        Venda venda = vendaRepository.findById(idVenda)
                .orElseThrow(() -> new ValidationException("Venda não encontrada."));

        // CORREÇÃO: Busca o Cliente pelo documento
        Cliente cliente = null;
        if (venda.getClienteDocumento() != null) {
            String docLimpo = venda.getClienteDocumento().replaceAll("\\D", "");
            cliente = clienteRepository.findByDocumento(docLimpo)
                    .orElseThrow(() -> new ValidationException("Cliente não encontrado para o documento: " + venda.getClienteDocumento()));
        } else {
            throw new ValidationException("Venda sem documento de cliente. Não é possível emitir NF-e.");
        }

        // 2. Valida Cliente (agora passando o objeto Cliente)
        validarDadosDestinatario(cliente);

        try {
            // 3. Preparação dos Dados para a Chave
            String nNF = String.valueOf(new Random().nextInt(99999) + 1); // Em produção, usar sequência do banco
            String cnf = String.format("%08d", new Random().nextInt(99999999));
            String cnpjEmitente = "57648950000144";
            String modelo = "55";
            String serie = "1";
            String tipoEmissao = "1"; // 1 = Normal

            // GERAÇÃO MANUAL DA CHAVE
            String chaveSemDigito = gerarChaveAcessoManual(
                    configuracoesNfe.getEstado().getCodigoUF(),
                    LocalDateTime.now(),
                    cnpjEmitente,
                    modelo,
                    serie,
                    nNF,
                    tipoEmissao,
                    cnf
            );

            // Calcula o Dígito Verificador (DV)
            String dv = calcularDigitoVerificador(chaveSemDigito);
            String chaveAcesso = chaveSemDigito + dv;

            // 4. Montagem do Objeto NFe
            TNFe nfe = new TNFe();
            TNFe.InfNFe infNFe = new TNFe.InfNFe();
            infNFe.setId("NFe" + chaveAcesso);
            infNFe.setVersao("4.00");

            infNFe.setIde(montarIdentificacao(cnf, nNF, dv));
            infNFe.setEmit(montarEmitente());

            // CORREÇÃO: Passando o objeto Cliente buscado
            infNFe.setDest(montarDestinatario(cliente));

            // Adiciona os Itens
            List<TNFe.InfNFe.Det> detalhes = montarDetalhes(venda.getItens());
            infNFe.getDet().addAll(detalhes);

            infNFe.setTotal(montarTotal(venda));
            infNFe.setTransp(montarTransporte());
            infNFe.setPag(montarPagamento(venda));

            nfe.setInfNFe(infNFe);

            // 5. Envio para a SEFAZ
            TEnviNFe enviNFe = new TEnviNFe();
            enviNFe.setVersao("4.00");
            enviNFe.setIdLote("1");
            enviNFe.setIndSinc("1"); // 1 = Síncrono
            enviNFe.getNFe().add(nfe);

            TRetEnviNFe retorno = Nfe.enviarNfe(configuracoesNfe, enviNFe, DocumentoEnum.NFE);

            // 6. Processar Retorno
            String status;
            String motivo;
            String xmlFinal = "";

            if (retorno.getProtNFe() != null && retorno.getProtNFe().getInfProt() != null) {
                status = retorno.getProtNFe().getInfProt().getCStat();
                motivo = retorno.getProtNFe().getInfProt().getXMotivo();
                xmlFinal = XmlNfeUtil.criaNfeProc(enviNFe, retorno.getProtNFe());
            } else {
                status = retorno.getCStat();
                motivo = retorno.getXMotivo();
                throw new ValidationException("Erro no Lote (Nota não processada): " + status + " - " + motivo);
            }

            if (status.equals("100")) {
                venda.setStatusNfce(StatusFiscal.APROVADA);
                venda.setChaveAcessoNfce(xmlFinal);
                vendaRepository.save(venda);
            } else {
                throw new ValidationException("Nota Rejeitada pela SEFAZ: " + status + " - " + motivo);
            }

            return new NfceResponseDTO(
                    chaveAcesso, nNF, "1", status, motivo, xmlFinal, LocalDateTime.now()
            );

        } catch (Exception e) {
            e.printStackTrace();
            throw new ValidationException("Erro ao emitir NFe: " + e.getMessage());
        }
    }

    // ================= MÉTODOS AUXILIARES =================

    private TNFe.InfNFe.Ide montarIdentificacao(String cnf, String nNF, String dv) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(configuracoesNfe.getEstado().getCodigoUF());
        ide.setCNF(cnf);
        ide.setNatOp("VENDA DE MERCADORIA");
        ide.setMod("55");
        ide.setSerie("1");
        ide.setNNF(nNF);
        ide.setDhEmi(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setDhSaiEnt(XmlNfeUtil.dataNfe(LocalDateTime.now()));
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG("2611606"); // Recife
        ide.setTpImp("1");
        ide.setTpEmis("1");
        ide.setCDV(dv);
        ide.setTpAmb(configuracoesNfe.getAmbiente().getCodigo());
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("1.0");
        return ide;
    }

    private TNFe.InfNFe.Emit montarEmitente() {
        TNFe.InfNFe.Emit emit = new TNFe.InfNFe.Emit();
        emit.setCNPJ("57648950000144");
        emit.setXNome("DD COSMETICOS LTDA");
        emit.setIE("113339363");
        emit.setCRT("1");

        TEnderEmi enderEmit = new TEnderEmi();
        enderEmit.setXLgr("RUA DO TESTE");
        enderEmit.setNro("123");
        enderEmit.setXBairro("CENTRO");
        enderEmit.setCMun("2611606");
        enderEmit.setXMun("RECIFE");
        enderEmit.setUF(TUfEmi.PE);
        enderEmit.setCEP("50000000");
        enderEmit.setCPais("1058");
        enderEmit.setXPais("BRASIL");
        emit.setEnderEmit(enderEmit);
        return emit;
    }

    private TNFe.InfNFe.Dest montarDestinatario(Cliente cliente) {
        TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();

        if (configuracoesNfe.getAmbiente().equals(AmbienteEnum.HOMOLOGACAO)) {
            dest.setXNome("NF-E EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL");
            dest.setIndIEDest("9");
        } else {
            dest.setXNome(cliente.getNome());
            String doc = cliente.getDocumento().replaceAll("\\D", "");
            if (doc.length() == 11) dest.setCPF(doc);
            else dest.setCNPJ(doc);
            dest.setIndIEDest("9");
        }

        TEndereco enderDest = new TEndereco();
        // Em um cenário real, pegar do objeto Cliente
        enderDest.setXLgr("RUA DO CLIENTE");
        enderDest.setNro("999");
        enderDest.setXBairro("BOA VIAGEM");
        enderDest.setCMun("2611606");
        enderDest.setXMun("RECIFE");
        enderDest.setUF(TUf.PE);
        enderDest.setCEP("51000000");
        enderDest.setCPais("1058");
        enderDest.setXPais("BRASIL");
        dest.setEnderDest(enderDest);
        return dest;
    }

    private List<TNFe.InfNFe.Det> montarDetalhes(List<ItemVenda> itens) {
        List<TNFe.InfNFe.Det> listaDetalhes = new ArrayList<>();
        int numeroItem = 1;
        ObjectFactory factory = new ObjectFactory();

        for (ItemVenda item : itens) {
            TNFe.InfNFe.Det det = new TNFe.InfNFe.Det();
            det.setNItem(String.valueOf(numeroItem));

            TNFe.InfNFe.Det.Prod prod = new TNFe.InfNFe.Det.Prod();
            prod.setCProd(String.valueOf(item.getProduto().getId()));
            prod.setCEAN("SEM GTIN");

            prod.setXProd(item.getProduto().getDescricao());

            prod.setNCM("33049990"); // Deveria vir do produto
            prod.setCFOP("5102");
            prod.setUCom("UN");

            BigDecimal quantidade = new BigDecimal(String.valueOf(item.getQuantidade()));
            BigDecimal valorUnitario = new BigDecimal(String.valueOf(item.getPrecoUnitario()));
            BigDecimal subtotalCalculado = quantidade.multiply(valorUnitario);

            prod.setQCom(formatarValor(quantidade, 4));
            prod.setVUnCom(formatarValor(valorUnitario, 10));
            prod.setVProd(formatarValor(subtotalCalculado, 2));

            prod.setCEANTrib("SEM GTIN");
            prod.setUTrib("UN");
            prod.setQTrib(prod.getQCom());
            prod.setVUnTrib(prod.getVUnCom());
            prod.setIndTot("1");
            det.setProd(prod);

            // TRIBUTOS (Simplificado para Simples Nacional)
            TNFe.InfNFe.Det.Imposto imposto = new TNFe.InfNFe.Det.Imposto();

            TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();
            TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 icms102 = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
            icms102.setOrig("0");
            icms102.setCSOSN("102");
            icms.setICMSSN102(icms102);
            imposto.getContent().add(factory.createTNFeInfNFeDetImpostoICMS(icms));

            TNFe.InfNFe.Det.Imposto.PIS pis = new TNFe.InfNFe.Det.Imposto.PIS();
            TNFe.InfNFe.Det.Imposto.PIS.PISOutr pisOutr = new TNFe.InfNFe.Det.Imposto.PIS.PISOutr();
            pisOutr.setCST("99");
            pisOutr.setVBC("0.00");
            pisOutr.setPPIS("0.00");
            pisOutr.setVPIS("0.00");
            pis.setPISOutr(pisOutr);
            imposto.getContent().add(factory.createTNFeInfNFeDetImpostoPIS(pis));

            TNFe.InfNFe.Det.Imposto.COFINS cofins = new TNFe.InfNFe.Det.Imposto.COFINS();
            TNFe.InfNFe.Det.Imposto.COFINS.COFINSOutr cofinsOutr = new TNFe.InfNFe.Det.Imposto.COFINS.COFINSOutr();
            cofinsOutr.setCST("99");
            cofinsOutr.setVBC("0.00");
            cofinsOutr.setPCOFINS("0.00");
            cofinsOutr.setVCOFINS("0.00");
            cofins.setCOFINSOutr(cofinsOutr);
            imposto.getContent().add(factory.createTNFeInfNFeDetImpostoCOFINS(cofins));

            det.setImposto(imposto);
            listaDetalhes.add(det);
            numeroItem++;
        }
        return listaDetalhes;
    }

    private TNFe.InfNFe.Total montarTotal(Venda venda) {
        TNFe.InfNFe.Total total = new TNFe.InfNFe.Total();
        TNFe.InfNFe.Total.ICMSTot icmsTot = new TNFe.InfNFe.Total.ICMSTot();
        icmsTot.setVBC("0.00");
        icmsTot.setVICMS("0.00");
        icmsTot.setVICMSDeson("0.00");
        icmsTot.setVFCP("0.00");
        icmsTot.setVBCST("0.00");
        icmsTot.setVST("0.00");
        icmsTot.setVFCPST("0.00");
        icmsTot.setVFCPSTRet("0.00");

        String valorTotal = formatarValor(venda.getValorTotal(), 2);

        icmsTot.setVProd(valorTotal);
        icmsTot.setVFrete("0.00");
        icmsTot.setVSeg("0.00");
        icmsTot.setVDesc("0.00");
        icmsTot.setVII("0.00");
        icmsTot.setVIPI("0.00");
        icmsTot.setVIPIDevol("0.00");
        icmsTot.setVPIS("0.00");
        icmsTot.setVCOFINS("0.00");
        icmsTot.setVOutro("0.00");
        icmsTot.setVNF(valorTotal);
        total.setICMSTot(icmsTot);
        return total;
    }

    private TNFe.InfNFe.Transp montarTransporte() {
        TNFe.InfNFe.Transp transp = new TNFe.InfNFe.Transp();
        transp.setModFrete("9");
        return transp;
    }

    private TNFe.InfNFe.Pag montarPagamento(Venda venda) {
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        TNFe.InfNFe.Pag.DetPag detPag = new TNFe.InfNFe.Pag.DetPag();
        detPag.setTPag("01"); // Dinheiro
        detPag.setVPag(formatarValor(venda.getValorTotal(), 2));
        pag.getDetPag().add(detPag);
        return pag;
    }

    private void validarDadosDestinatario(Cliente cliente) {
        if (cliente == null) {
            throw new ValidationException("Para emitir NF-e, a venda deve ter um cliente identificado.");
        }
        if (cliente.getDocumento() == null || cliente.getDocumento().length() < 11) {
            throw new ValidationException("CPF/CNPJ do cliente inválido.");
        }
    }

    // ================= MÉTODOS AUXILIARES BLINDADOS =================

    private String formatarValor(Object valor, int casasDecimais) {
        if (valor == null) return "0.00";
        BigDecimal bd;
        if (valor instanceof Double) {
            bd = BigDecimal.valueOf((Double) valor);
        } else if (valor instanceof BigDecimal) {
            bd = (BigDecimal) valor;
        } else {
            bd = new BigDecimal(valor.toString());
        }
        return bd.setScale(casasDecimais, RoundingMode.HALF_EVEN).toString();
    }

    private String gerarChaveAcessoManual(String cUF, LocalDateTime data, String cnpj, String mod, String serie, String nNF, String tpEmis, String cNF) {
        StringBuilder chave = new StringBuilder();
        chave.append(String.format("%02d", Integer.parseInt(cUF)));
        chave.append(data.format(DateTimeFormatter.ofPattern("yyMM")));
        chave.append(String.format("%014d", Long.parseLong(cnpj.replaceAll("\\D", ""))));
        chave.append(String.format("%02d", Integer.parseInt(mod)));
        chave.append(String.format("%03d", Integer.parseInt(serie)));
        chave.append(String.format("%09d", Long.parseLong(nNF)));
        chave.append(tpEmis);
        chave.append(String.format("%08d", Long.parseLong(cNF)));
        return chave.toString();
    }

    private String calcularDigitoVerificador(String chave43) {
        int soma = 0;
        int peso = 2;
        for (int i = chave43.length() - 1; i >= 0; i--) {
            int num = Character.getNumericValue(chave43.charAt(i));
            soma += num * peso;
            peso++;
            if (peso > 9) peso = 2;
        }
        int resto = soma % 11;
        int dv = 11 - resto;
        if (dv >= 10) dv = 0;
        return String.valueOf(dv);
    }
}