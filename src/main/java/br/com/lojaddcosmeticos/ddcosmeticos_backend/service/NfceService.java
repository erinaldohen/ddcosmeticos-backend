package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.NfceResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class NfceService {

    // Simulação de injeção do KeyStore (Configurado no NfeConfig)
    // Em produção real, você injetaria o KeyStore carregado
    private final KeyStore keyStoreNfe;

    public NfceService(KeyStore keyStoreNfe) {
        this.keyStoreNfe = keyStoreNfe;
    }

    @Transactional
    public NfceResponseDTO emitirNfce(Venda venda) {
        try {
            // 1. FILTRAGEM INTELIGENTE (REGRA DE NEGÓCIO)
            // Seleciona apenas produtos que têm origem fiscal confirmada
            List<ItemVenda> itensFiscais = venda.getItens().stream()
                    .filter(item -> item.getProduto().isPossuiNfEntrada())
                    .collect(Collectors.toList());

            // Se não sobrar nada, não emite nota
            if (itensFiscais.isEmpty()) {
                return NfceResponseDTO.builder()
                        .autorizada(false)
                        .statusSefaz("Venda sem itens fiscais elegíveis. Emissão dispensada.")
                        .xmlNfce(null)
                        .numeroNota(null)
                        .build();
            }

            // 2. RECALCULAR TOTAIS FISCAIS
            // O valor da nota NÃO é o valor da venda, é a soma dos itens fiscais apenas.
            BigDecimal totalProdutosFiscal = BigDecimal.ZERO;
            for (ItemVenda item : itensFiscais) {
                totalProdutosFiscal = totalProdutosFiscal.add(item.getValorTotalItem());
            }

            // Rateio de desconto proporcional (Simplificado para o exemplo)
            BigDecimal descontoFiscal = BigDecimal.ZERO;
            BigDecimal totalLiquidoFiscal = totalProdutosFiscal.subtract(descontoFiscal);

            // 3. MONTAGEM DO XML (Apenas Itens Fiscais)
            String xmlAssinado = gerarXmlAssinado(venda, itensFiscais, totalProdutosFiscal, totalLiquidoFiscal);

            // 4. RETORNO SUCESSO
            return NfceResponseDTO.builder()
                    .autorizada(true) // Em produção real, aqui viria o status da SEFAZ
                    .numeroNota("NFC-" + venda.getId() + System.currentTimeMillis() % 1000)
                    .protocolo("PROTO-" + System.currentTimeMillis())
                    .xmlNfce(xmlAssinado)
                    .statusSefaz("100 - Autorizado o uso da NF-e")
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            return NfceResponseDTO.builder()
                    .autorizada(false)
                    .statusSefaz("Erro na emissão: " + e.getMessage())
                    .build();
        }
    }

    private String gerarXmlAssinado(Venda venda, List<ItemVenda> itens, BigDecimal totalProd, BigDecimal totalLiq) throws Exception {
        StringBuilder xml = new StringBuilder();

        // Cabeçalho Básico NFC-e
        xml.append("<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\">")
                .append("<infNFe Id=\"NFe").append(System.currentTimeMillis()).append("\" versao=\"4.00\">");

        // Identificação
        xml.append("<ide>")
                .append("<cUF>26</cUF>") // PE
                .append("<natOp>VENDA AO CONSUMIDOR</natOp>")
                .append("<mod>65</mod>") // 65 = NFC-e
                .append("<serie>1</serie>")
                .append("<nNF>").append(venda.getId()).append("</nNF>")
                .append("<dhEmi>").append(venda.getDataVenda().format(DateTimeFormatter.ISO_DATE_TIME)).append("</dhEmi>")
                .append("<tpEmis>1</tpEmis>") // Normal
                .append("</ide>");

        // Emitente (Dados da DD Cosméticos - Fixos ou do Banco)
        xml.append("<emit>")
                .append("<CNPJ>00000000000191</CNPJ>")
                .append("<xNome>DD COSMETICOS LTDA</xNome>")
                .append("</emit>");

        // Detalhes dos Produtos (Loop apenas nos Fiscais)
        int nItem = 1;
        for (ItemVenda item : itens) {
            xml.append("<det nItem=\"").append(nItem).append("\">")
                    .append("<prod>")
                    .append("<cProd>").append(item.getProduto().getCodigoBarras()).append("</cProd>")
                    .append("<xProd>").append(item.getProduto().getDescricao()).append("</xProd>")
                    .append("<NCM>33051000</NCM>") // Exemplo, deveria vir do cadastro
                    .append("<CFOP>5102</CFOP>")
                    .append("<uCom>UN</uCom>")
                    .append("<qCom>").append(item.getQuantidade()).append("</qCom>")
                    .append("<vUnCom>").append(item.getPrecoUnitario()).append("</vUnCom>")
                    .append("<vProd>").append(item.getValorTotalItem()).append("</vProd>")
                    .append("</prod>")
                    .append("<imposto><ICMS><ICMSSN102><orig>0</orig><CSOSN>102</CSOSN></ICMSSN102></ICMS></imposto>")
                    .append("</det>");
            nItem++;
        }

        // Totais (Recalculados)
        xml.append("<total><ICMSTot>")
                .append("<vBC>0.00</vBC>")
                .append("<vICMS>0.00</vICMS>")
                .append("<vProd>").append(totalProd).append("</vProd>")
                .append("<vNF>").append(totalLiq).append("</vNF>") // Total a Pagar na Nota
                .append("</ICMSTot></total>");

        xml.append("</infNFe>");

        // Simulação de Assinatura Digital (Placeholder para lógica real de assinatura XML-DSig)
        // Em produção, usa-se a biblioteca Java XML Digital Signature API
        xml.append("<Signature>ASSINATURA_DIGITAL_GERADA_AQUI_BASE64_").append(assinaturaSimulada()).append("</Signature>");

        xml.append("</NFe>");
        return xml.toString();
    }

    private String assinaturaSimulada() {
        return Long.toHexString(new Random().nextLong());
    }
}