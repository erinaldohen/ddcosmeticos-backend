package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO; // Importe o DTO
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class NfceService {

    /**
     * MÉTODO PRINCIPAL DE VENDA (CORRIGIDO)
     * Retorna o DTO estruturado em vez de apenas String.
     */
    public NfceResponseDTO emitirNfce(Venda venda) {
        try {
            // 1. Gera o XML
            String xml = gerarXmlNfceString(venda);

            // 2. Simula a Autorização na SEFAZ (Mock)
            // Em produção, você enviaria o XML para o webservice da SEFAZ aqui.
            String protocolo = "PROT" + System.currentTimeMillis();

            venda.setStatusFiscal("AUTORIZADO");
            venda.setXmlNfce(xml);

            // 3. Retorna o Objeto que o Controller espera
            return new NfceResponseDTO(
                    xml,
                    "AUTORIZADO",
                    protocolo,
                    "Nota Fiscal emitida com sucesso."
            );

        } catch (Exception e) {
            // Em caso de erro, retorna DTO de erro
            return new NfceResponseDTO(
                    null,
                    "ERRO_EMISSAO",
                    null,
                    "Falha ao gerar NFC-e: " + e.getMessage()
            );
        }
    }

    /**
     * Método auxiliar interno para montar a String do XML de Venda
     */
    private String gerarXmlNfceString(Venda venda) {
        return "<NFe><infNFe Id=\"NFe" + System.currentTimeMillis() + "\">" +
                "<ide><nNF>" + venda.getId() + "</nNF><dhEmi>" + venda.getDataVenda() + "</dhEmi></ide>" +
                "<total><vNF>" + venda.getTotalVenda() + "</vNF></total>" +
                "</infNFe><Signature>ASSINATURA_DIGITAL_AQUI</Signature></NFe>";
    }

    /**
     * Gera XML de Baixa de Estoque (CFOP 5.927).
     */
    public String gerarXmlBaixaEstoque(Produto produto, BigDecimal quantidade, String motivo) {
        try {
            BigDecimal custo = produto.getPrecoCustoInicial() != null ? produto.getPrecoCustoInicial() : BigDecimal.ZERO;
            BigDecimal pmp = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;
            BigDecimal valorTotal = quantidade.multiply(pmp);

            StringBuilder xml = new StringBuilder();
            xml.append("<NFe> <infNFe Id=\"NFe_BAIXA_" + System.currentTimeMillis() + "\">");

            xml.append("<ide>")
                    .append("<natOp>BAIXA DE ESTOQUE - PERDA/ROUBO</natOp>")
                    .append("<mod>55</mod>")
                    .append("<serie>1</serie>")
                    .append("<nNF>" + (System.currentTimeMillis() % 10000) + "</nNF>")
                    .append("<dhEmi>" + LocalDateTime.now() + "</dhEmi>")
                    .append("<tpNF>1</tpNF>") // 1=Saída
                    .append("<idDest>1</idDest>") // Operação Interna
                    .append("</ide>");

            xml.append("<emit><CNPJ>00000000000191</CNPJ><xNome>DD COSMETICOS</xNome></emit>");
            xml.append("<dest><CNPJ>00000000000191</CNPJ><xNome>DD COSMETICOS - BAIXA ESTOQUE</xNome></dest>");

            xml.append("<det nItem=\"1\">")
                    .append("<prod>")
                    .append("<cProd>" + produto.getCodigoBarras() + "</cProd>")
                    .append("<xProd>" + produto.getDescricao() + "</xProd>")
                    .append("<CFOP>5927</CFOP>")
                    .append("<qCom>" + quantidade + "</qCom>")
                    .append("<vUnCom>" + custo + "</vUnCom>")
                    .append("<vProd>" + valorTotal + "</vProd>")
                    .append("</prod>")
                    .append("</det>");

            xml.append("<infAdic><infCpl>Motivo da Baixa: " + motivo + "</infCpl></infAdic>");
            xml.append("</infNFe></NFe>");

            return xml.toString();
        } catch (Exception e) {
            return "ERRO AO GERAR XML BAIXA: " + e.getMessage();
        }
    }

    /**
     * Gera XML de Entrada (CFOP 1.102) para compras de Fornecedor Pessoa Física (CPF).
     */
    public String gerarXmlNotaEntradaPF(Produto produto, BigDecimal quantidade, Fornecedor fornecedorPF) {
        try {
            BigDecimal custo = produto.getPrecoCustoInicial() != null ? produto.getPrecoCustoInicial() : BigDecimal.ZERO;

            StringBuilder xml = new StringBuilder();
            xml.append("<NFe> <infNFe Id=\"NFe_ENTRADA_" + System.currentTimeMillis() + "\" versao=\"4.00\">");

            xml.append("<ide>")
                    .append("<natOp>COMPRA PARA COMERCIALIZACAO</natOp>")
                    .append("<mod>55</mod>")
                    .append("<serie>1</serie>")
                    .append("<nNF>" + (System.currentTimeMillis() % 100000) + "</nNF>")
                    .append("<dhEmi>" + LocalDateTime.now() + "</dhEmi>")
                    .append("<tpNF>0</tpNF>") // 0 = ENTRADA
                    .append("<idDest>1</idDest>")
                    .append("</ide>");

            xml.append("<emit><CNPJ>00000000000191</CNPJ><xNome>DD COSMETICOS LTDA</xNome></emit>");

            xml.append("<dest>")
                    .append("<CPF>" + fornecedorPF.getCpfOuCnpj() + "</CPF>")
                    .append("<xNome>" + fornecedorPF.getRazaoSocial() + "</xNome>")
                    .append("<indIEDest>9</indIEDest>")
                    .append("</dest>");

            xml.append("<det nItem=\"1\">")
                    .append("<prod>")
                    .append("<cProd>" + produto.getCodigoBarras() + "</cProd>")
                    .append("<xProd>" + produto.getDescricao() + "</xProd>")
                    .append("<NCM>" + (produto.getNcm() != null ? produto.getNcm() : "00000000") + "</NCM>")
                    .append("<CFOP>1102</CFOP>")
                    .append("<qCom>" + quantidade + "</qCom>")
                    .append("<vUnCom>" + custo + "</vUnCom>")
                    .append("<vProd>" + quantidade.multiply(custo) + "</vProd>")
                    .append("</prod>")
                    .append("</det>");

            xml.append("</infNFe></NFe>");

            return xml.toString();
        } catch (Exception e) {
            return "ERRO XML ENTRADA: " + e.getMessage();
        }
    }
}