package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.DashedBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Service
public class DocumentoFiscalPdfService {

    // ==========================================================
    // 1. GERADOR DE CUPOM FISCAL (NFC-E) - FORMATO TÉRMICO 80mm
    // ==========================================================
    public byte[] gerarCupomNfce(Venda venda, ConfiguracaoLoja config) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);

        // Largura de 80mm (aprox 226 points). Altura longa para caber os itens.
        pdf.setDefaultPageSize(new PageSize(226f, 1000f));
        Document document = new Document(pdf);
        document.setMargins(10, 10, 10, 10);

        // CABEÇALHO DA EMPRESA
        document.add(new Paragraph(config.getLoja().getRazaoSocial()).setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(9));
        document.add(new Paragraph("CNPJ: " + config.getLoja().getCnpj()).setTextAlignment(TextAlignment.CENTER).setFontSize(7));
        document.add(new Paragraph(config.getEndereco().getLogradouro() + ", " + config.getEndereco().getNumero() + " - " + config.getEndereco().getCidade()).setTextAlignment(TextAlignment.CENTER).setFontSize(7));

        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));
        document.add(new Paragraph("Documento Auxiliar da Nota Fiscal de Consumidor Eletrônica").setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(7));
        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

        // TABELA DE ITENS
        Table table = new Table(UnitValue.createPercentArray(new float[]{45, 15, 15, 25})).useAllAvailableWidth();
        table.addCell(new Cell().add(new Paragraph("DESC")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
        table.addCell(new Cell().add(new Paragraph("QTD")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
        table.addCell(new Cell().add(new Paragraph("UN")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
        table.addCell(new Cell().add(new Paragraph("VL TOT")).setBorder(Border.NO_BORDER).setFontSize(7).setBold().setTextAlignment(TextAlignment.RIGHT));

        for (ItemVenda item : venda.getItens()) {
            table.addCell(new Cell().add(new Paragraph(item.getProduto().getDescricao())).setBorder(Border.NO_BORDER).setFontSize(6));
            table.addCell(new Cell().add(new Paragraph(item.getQuantidade().setScale(0, RoundingMode.DOWN).toString())).setBorder(Border.NO_BORDER).setFontSize(6));
            table.addCell(new Cell().add(new Paragraph("UN")).setBorder(Border.NO_BORDER).setFontSize(6));
            table.addCell(new Cell().add(new Paragraph(item.getPrecoUnitario().multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP).toString())).setBorder(Border.NO_BORDER).setFontSize(6).setTextAlignment(TextAlignment.RIGHT));
        }
        document.add(table);

        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

        // TOTAIS E PAGAMENTO
        document.add(new Paragraph("QTD TOTAL DE ITENS: " + venda.getItens().size()).setFontSize(7));
        if (venda.getDescontoTotal().compareTo(java.math.BigDecimal.ZERO) > 0) {
            document.add(new Paragraph("DESCONTOS R$: " + venda.getDescontoTotal()).setFontSize(7));
        }
        document.add(new Paragraph("VALOR TOTAL R$: " + venda.getValorTotal()).setBold().setFontSize(9));
        document.add(new Paragraph("FORMA PAGAMENTO: " + venda.getFormaDePagamento()).setFontSize(7));

        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

        // DADOS DO CONSUMIDOR E NOTA
        document.add(new Paragraph("Consumidor: " + (venda.getClienteNome() != null ? venda.getClienteNome() : "Consumidor Final")).setFontSize(7));
        document.add(new Paragraph("CPF/CNPJ: " + (venda.getClienteDocumento() != null ? venda.getClienteDocumento() : "Não informado")).setFontSize(7));
        document.add(new Paragraph("Emissão: " + venda.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + " | Num: " + venda.getNumeroNfce() + " | Série: " + venda.getSerieNfce()).setFontSize(7));

        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

        // CHAVE E QR CODE
        document.add(new Paragraph("Consulte pela Chave de Acesso em:").setTextAlignment(TextAlignment.CENTER).setFontSize(7));
        document.add(new Paragraph("http://nfce.sefaz.pe.gov.br/nfce/consulta").setTextAlignment(TextAlignment.CENTER).setFontSize(7));

        String chaveStr = venda.getChaveAcessoNfce() != null ? venda.getChaveAcessoNfce() : "Aguardando Sefaz";
        document.add(new Paragraph(chaveStr).setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(7));

        if (venda.getUrlQrCode() != null && !venda.getUrlQrCode().isBlank()) {
            BarcodeQRCode qrCode = new BarcodeQRCode(venda.getUrlQrCode());
            Image qrCodeImage = new Image(qrCode.createFormXObject(pdf));
            qrCodeImage.setWidth(100f);
            qrCodeImage.setHeight(100f);
            qrCodeImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
            document.add(qrCodeImage);
        }

        document.close();
        return out.toByteArray();
    }

    // ==========================================================
    // 2. GERADOR DE DANFE (NF-E) - FORMATO A4 PARA EMPRESAS
    // ==========================================================
    public byte[] gerarDanfeNfe(Venda venda, ConfiguracaoLoja config) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);

        // Formato A4 Padrão
        pdf.setDefaultPageSize(PageSize.A4);
        Document document = new Document(pdf);
        document.setMargins(20, 20, 20, 20);

        // CABEÇALHO DANFE
        Table header = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();

        Cell emitente = new Cell().add(new Paragraph(config.getLoja().getRazaoSocial()).setBold().setFontSize(12));
        emitente.add(new Paragraph("CNPJ: " + config.getLoja().getCnpj()).setFontSize(10));
        emitente.add(new Paragraph(config.getEndereco().getLogradouro() + ", " + config.getEndereco().getNumero()).setFontSize(9));
        header.addCell(emitente);

        Cell dadosNota = new Cell().add(new Paragraph("DANFE").setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER));
        dadosNota.add(new Paragraph("Documento Auxiliar da Nota Fiscal Eletrônica").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        dadosNota.add(new Paragraph("Nº: " + venda.getNumeroNfce() + " | Série: " + venda.getSerieNfce()).setBold().setFontSize(11).setTextAlignment(TextAlignment.CENTER));

        String chaveStr = venda.getChaveAcessoNfce() != null ? venda.getChaveAcessoNfce() : "Aguardando Sefaz";
        dadosNota.add(new Paragraph("CHAVE DE ACESSO:\n" + chaveStr).setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        header.addCell(dadosNota);

        document.add(header);

        // DADOS DO DESTINATÁRIO (B2B)
        document.add(new Paragraph("\nDESTINATÁRIO / REMETENTE").setBold().setFontSize(10).setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY));
        Table dest = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        dest.addCell(new Cell().add(new Paragraph("NOME / RAZÃO SOCIAL: " + venda.getClienteNome()).setFontSize(9)));
        dest.addCell(new Cell().add(new Paragraph("CNPJ / CPF: " + venda.getClienteDocumento()).setFontSize(9)));
        document.add(dest);

        // TOTAIS
        document.add(new Paragraph("\nCÁLCULO DO IMPOSTO / TOTAIS").setBold().setFontSize(10).setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY));
        Table totais = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
        totais.addCell(new Cell().add(new Paragraph("VALOR DOS PRODUTOS: R$ " + venda.getValorTotal().add(venda.getDescontoTotal())).setFontSize(9)));
        totais.addCell(new Cell().add(new Paragraph("VALOR TOTAL DA NOTA: R$ " + venda.getValorTotal()).setBold().setFontSize(10)));
        document.add(totais);

        // ITENS
        document.add(new Paragraph("\nDADOS DOS PRODUTOS / SERVIÇOS").setBold().setFontSize(10).setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY));
        Table tableItens = new Table(UnitValue.createPercentArray(new float[]{15, 45, 10, 10, 20})).useAllAvailableWidth();
        tableItens.addCell(new Cell().add(new Paragraph("CÓDIGO").setFontSize(8).setBold()));
        tableItens.addCell(new Cell().add(new Paragraph("DESCRIÇÃO").setFontSize(8).setBold()));
        tableItens.addCell(new Cell().add(new Paragraph("QTD").setFontSize(8).setBold()));
        tableItens.addCell(new Cell().add(new Paragraph("UN").setFontSize(8).setBold()));
        tableItens.addCell(new Cell().add(new Paragraph("V. TOTAL").setFontSize(8).setBold()));

        for (ItemVenda item : venda.getItens()) {
            tableItens.addCell(new Cell().add(new Paragraph(item.getProduto().getCodigoBarras() != null ? item.getProduto().getCodigoBarras() : item.getProduto().getId().toString())).setFontSize(8));
            tableItens.addCell(new Cell().add(new Paragraph(item.getProduto().getDescricao())).setFontSize(8));
            tableItens.addCell(new Cell().add(new Paragraph(item.getQuantidade().setScale(2, RoundingMode.HALF_UP).toString())).setFontSize(8));
            tableItens.addCell(new Cell().add(new Paragraph("UN")).setFontSize(8));
            tableItens.addCell(new Cell().add(new Paragraph(item.getPrecoUnitario().multiply(item.getQuantidade()).setScale(2, RoundingMode.HALF_UP).toString())).setFontSize(8));
        }
        document.add(tableItens);

        document.close();
        return out.toByteArray();
    }
}