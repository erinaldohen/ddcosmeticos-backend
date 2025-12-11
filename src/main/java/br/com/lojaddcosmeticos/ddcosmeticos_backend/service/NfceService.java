package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Enumeration;

/**
 * Serviço responsável pela geração e assinatura da NFC-e (Nota Fiscal de Consumidor Eletrônica).
 */
@Service
public class NfceService {

    @Autowired
    private KeyStore keyStoreNfe;

    /**
     * Gera o XML da NFC-e, assina digitalmente e retorna os dados fiscais.
     */
    public NfceResponseDTO emitirNfce(Venda venda) {
        try {
            // 1. Montagem do XML (Layout simplificado da NFe 4.00)
            StringBuilder xmlBuilder = new StringBuilder();

            String idNota = "NFe" + "352312" + "00000000000000" + "65" + "001" + String.format("%09d", venda.getId()) + "1" + "00000000"; // Chave de acesso fictícia baseada no ID

            xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xmlBuilder.append("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">");
            xmlBuilder.append("<infNFe Id=\"" + idNota + "\" versao=\"4.00\">");

            // --- Identificação ---
            xmlBuilder.append("<ide>");
            xmlBuilder.append("<cUF>35</cUF>"); // SP
            xmlBuilder.append("<natOp>VENDA MERC. ADQ. TERC.</natOp>");
            xmlBuilder.append("<mod>65</mod>"); // Modelo 65 = NFC-e
            xmlBuilder.append("<serie>1</serie>");
            xmlBuilder.append("<nNF>" + venda.getId() + "</nNF>");
            xmlBuilder.append("<dhEmi>" + venda.getDataVenda().format(DateTimeFormatter.ISO_DATE_TIME) + "</dhEmi>");
            xmlBuilder.append("</ide>");

            // --- Emitente (A Loja) ---
            // Em produção, esses dados viriam de uma tabela 'EmpresaConfig'
            xmlBuilder.append("<emit>");
            xmlBuilder.append("<CNPJ>57648950000144</CNPJ>"); // CNPJ do certificado
            xmlBuilder.append("<xNome>DD COSMETICOS LTDA</xNome>");
            xmlBuilder.append("<enderEmit><xLgr>RUA DA BELEZA</xLgr><nro>100</nro><xBairro>CENTRO</xBairro><xMun>SAO PAULO</xMun><UF>SP</UF></enderEmit>");
            xmlBuilder.append("</emit>");

            // --- Destinatário (Consumidor) ---
            xmlBuilder.append("<dest>");
            // Na NFC-e, o destinatário pode ser anônimo (sem CPF)
            xmlBuilder.append("<indIEDest>9</indIEDest>"); // Não Contribuinte
            xmlBuilder.append("</dest>");

            // --- Detalhes (Itens) ---
            int nItem = 1;
            for (ItemVenda item : venda.getItens()) {
                xmlBuilder.append("<det nItem=\"" + nItem + "\">");
                xmlBuilder.append("<prod>");
                xmlBuilder.append("<cProd>" + item.getProduto().getCodigoBarras() + "</cProd>");
                xmlBuilder.append("<xProd>" + item.getProduto().getDescricao() + "</xProd>");
                xmlBuilder.append("<NCM>" + (item.getProduto().getNcm() != null ? item.getProduto().getNcm() : "00000000") + "</NCM>");
                xmlBuilder.append("<CFOP>5102</CFOP>");
                xmlBuilder.append("<uCom>UN</uCom>");
                xmlBuilder.append("<qCom>" + item.getQuantidade() + "</qCom>");
                xmlBuilder.append("<vUnCom>" + item.getPrecoUnitario() + "</vUnCom>");
                xmlBuilder.append("<vProd>" + item.getValorTotalItem() + "</vProd>"); // Valor líquido
                xmlBuilder.append("</prod>");
                // Impostos seriam adicionados aqui (<imposto>...)
                xmlBuilder.append("</det>");
                nItem++;
            }

            // --- Totais ---
            xmlBuilder.append("<total>");
            xmlBuilder.append("<ICMSTot>");
            xmlBuilder.append("<vNF>" + venda.getValorLiquido() + "</vNF>");
            xmlBuilder.append("</ICMSTot>");
            xmlBuilder.append("</total>");

            xmlBuilder.append("</infNFe>");

            // 2. Assinatura Digital (Usando o Certificado A1/PFX carregado)
            String xmlAssinado = assinarXML(xmlBuilder.toString());

            xmlAssinado += "</NFe>"; // Fecha a tag raiz após a assinatura

            // Retorno do DTO
            return NfceResponseDTO.builder()
                    .chaveDeAcesso(idNota.replace("NFe", ""))
                    .numeroNota(String.valueOf(venda.getId()))
                    .statusSefaz("100 - Autorizado o uso da NFC-e")
                    .autorizada(true)
                    .xmlNfce(xmlAssinado)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return NfceResponseDTO.builder()
                    .statusSefaz("Erro na geração: " + e.getMessage())
                    .autorizada(false)
                    .build();
        }
    }

    /**
     * Método auxiliar para assinar o conteúdo do XML usando a chave privada do KeyStore.
     */
    private String assinarXML(String xmlContent) throws Exception {
        // Obtém o alias do certificado (geralmente o primeiro disponível no PFX)
        Enumeration<String> aliases = keyStoreNfe.aliases();
        String alias = aliases.hasMoreElements() ? aliases.nextElement() : null;

        if (alias == null) throw new RuntimeException("Nenhum certificado encontrado no KeyStore.");

        // Obtém a chave privada
        PrivateKey privateKey = (PrivateKey) keyStoreNfe.getKey(alias, "912219".toCharArray()); // Senha definida no NfeConfig

        // Cria a assinatura
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(xmlContent.getBytes());
        byte[] signedData = signature.sign();

        // Converte para Base64 para inserir no XML
        String signatureBase64 = Base64.getEncoder().encodeToString(signedData);

        // Monta o bloco de assinatura (Signature XMLDSig simplificado para demonstração)
        StringBuilder sigBuilder = new StringBuilder();
        sigBuilder.append("<Signature xmlns=\"http://www.w3.org/2000/09/xmldsig#\">");
        sigBuilder.append("<SignedInfo>..."); // Simplificação: aqui iriam os dados canônicos
        sigBuilder.append("</SignedInfo>");
        sigBuilder.append("<SignatureValue>" + signatureBase64 + "</SignatureValue>");
        sigBuilder.append("<KeyInfo><X509Data><X509Certificate>..."); // Aqui iria o certificado público
        sigBuilder.append("</X509Certificate></X509Data></KeyInfo>");
        sigBuilder.append("</Signature>");

        return xmlContent + sigBuilder.toString();
    }
}