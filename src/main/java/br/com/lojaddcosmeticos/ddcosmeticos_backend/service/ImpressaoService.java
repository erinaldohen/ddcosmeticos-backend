package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.colors.ColorConstants;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImpressaoService {

    private final VendaRepository vendaRepository;
    private final ConfiguracaoLojaService configuracaoLojaService; // <-- Puxa os dados REAIS da loja

    // =========================================================================
    // 1. CUPOM TÉRMICO (80mm) PARA CLIENTES FINAIS (B2C)
    // =========================================================================
    @Transactional(readOnly = true)
    public byte[] gerarCupomNfce(Long idVenda) {
        Venda venda = vendaRepository.findByIdComItens(idVenda)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada para impressão."));
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            // Largura de 80mm
            pdf.setDefaultPageSize(new PageSize(226f, 1500f));
            Document document = new Document(pdf);
            document.setMargins(10, 5, 10, 5);

            // --- CABEÇALHO DA EMPRESA REAIS ---
            document.add(new Paragraph(config.getLoja().getRazaoSocial() != null ? config.getLoja().getRazaoSocial() : "LOJA DD COSMETICOS")
                    .setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(9));
            document.add(new Paragraph("CNPJ: " + (config.getLoja().getCnpj() != null ? config.getLoja().getCnpj() : "Não Informado"))
                    .setTextAlignment(TextAlignment.CENTER).setFontSize(8));

            String endereco = config.getEndereco().getLogradouro() + ", " + config.getEndereco().getNumero() + "\n" +
                    config.getEndereco().getBairro() + " - " + config.getEndereco().getCidade() + "/" + config.getEndereco().getUf();
            document.add(new Paragraph(endereco).setTextAlignment(TextAlignment.CENTER).setFontSize(7));

            document.add(new Paragraph("---------------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

            boolean isFiscal = venda.getChaveAcessoNfce() != null && !venda.getChaveAcessoNfce().contains("SIMULADA");
            if (isFiscal) {
                document.add(new Paragraph("DANFE NFC-e").setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(9));
                document.add(new Paragraph("Documento Auxiliar da Nota Fiscal de Consumidor Eletrônica").setTextAlignment(TextAlignment.CENTER).setFontSize(7));
            } else {
                document.add(new Paragraph("RECIBO DE VENDA - SEM VALOR FISCAL").setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(9));
                document.add(new Paragraph("AMBIENTE DE TESTES / SIMULAÇÃO").setTextAlignment(TextAlignment.CENTER).setFontSize(7));
            }
            document.add(new Paragraph("---------------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

            // --- ITENS DA VENDA (SEM CORTES, COM EAN COMPLETO) ---
            Table table = new Table(UnitValue.createPercentArray(new float[]{15, 45, 10, 10, 20})).useAllAvailableWidth();
            table.addCell(new Cell().add(new Paragraph("CÓD")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
            table.addCell(new Cell().add(new Paragraph("DESCRIÇÃO")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
            table.addCell(new Cell().add(new Paragraph("QTD")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
            table.addCell(new Cell().add(new Paragraph("UN")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
            table.addCell(new Cell().add(new Paragraph("TOTAL")).setBorder(Border.NO_BORDER).setFontSize(7).setBold().setTextAlignment(TextAlignment.RIGHT));

            for (ItemVenda item : venda.getItens()) {
                String ean = item.getProduto().getCodigoBarras() != null && !item.getProduto().getCodigoBarras().isBlank() ? item.getProduto().getCodigoBarras() : item.getProduto().getId().toString();
                BigDecimal totalItem = item.getPrecoUnitario().multiply(item.getQuantidade()).subtract(item.getDesconto() != null ? item.getDesconto() : BigDecimal.ZERO);

                table.addCell(new Cell().add(new Paragraph(ean)).setBorder(Border.NO_BORDER).setFontSize(6));
                // Descrição completa sem cortar
                table.addCell(new Cell().add(new Paragraph(item.getProduto().getDescricao())).setBorder(Border.NO_BORDER).setFontSize(6));
                table.addCell(new Cell().add(new Paragraph(item.getQuantidade().setScale(0, RoundingMode.DOWN).toString())).setBorder(Border.NO_BORDER).setFontSize(6));
                table.addCell(new Cell().add(new Paragraph("UN")).setBorder(Border.NO_BORDER).setFontSize(6));
                table.addCell(new Cell().add(new Paragraph(totalItem.setScale(2, RoundingMode.HALF_UP).toString())).setBorder(Border.NO_BORDER).setFontSize(6).setTextAlignment(TextAlignment.RIGHT));
            }
            document.add(table);

            document.add(new Paragraph("---------------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

            // --- TOTAIS ---
            document.add(new Paragraph("QTD. TOTAL DE ITENS: " + venda.getItens().size()).setFontSize(8));
            document.add(new Paragraph("VALOR TOTAL R$: " + venda.getValorTotal().add(venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)).setFontSize(8));
            if (venda.getDescontoTotal() != null && venda.getDescontoTotal().compareTo(BigDecimal.ZERO) > 0) {
                document.add(new Paragraph("DESCONTOS R$: - " + venda.getDescontoTotal().setScale(2, RoundingMode.HALF_UP)).setFontSize(8));
            }
            document.add(new Paragraph("VALOR A PAGAR R$: " + venda.getValorTotal().setScale(2, RoundingMode.HALF_UP)).setBold().setFontSize(9));

            document.add(new Paragraph("---------------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));
            document.add(new Paragraph("FORMA PAGAMENTO                                VALOR PAGO").setBold().setFontSize(7));
            if (venda.getPagamentos() != null) {
                for (PagamentoVenda pg : venda.getPagamentos()) {
                    document.add(new Paragraph(String.format("%-40s %.2f", pg.getFormaPagamento(), pg.getValor())).setFontSize(7));
                }
            }

            // --- TRIBUTOS (Conforme exigido) ---
            BigDecimal tribFed = venda.getValorTotal().multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal tribEst = venda.getValorTotal().multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
            document.add(new Paragraph(String.format("\nTrib aprox R$ %.2f Fed, R$ %.2f Est. Fonte: Estimativa/IBPT", tribFed, tribEst)).setFontSize(6).setTextAlignment(TextAlignment.CENTER));

            document.add(new Paragraph("---------------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

            // --- RODAPÉ FISCAL ---
            if (isFiscal) {
                document.add(new Paragraph("Consulte pela Chave de Acesso em:").setTextAlignment(TextAlignment.CENTER).setFontSize(7).setBold());
                document.add(new Paragraph("http://nfce.sefaz.pe.gov.br/nfce/consulta").setTextAlignment(TextAlignment.CENTER).setFontSize(7));
                document.add(new Paragraph(venda.getChaveAcessoNfce()).setTextAlignment(TextAlignment.CENTER).setFontSize(7));

                document.add(new Paragraph("\nCONSUMIDOR: " + (venda.getClienteNome() != null ? venda.getClienteNome() : "NÃO IDENTIFICADO")).setFontSize(7));
                if (venda.getClienteDocumento() != null && !venda.getClienteDocumento().isBlank()) {
                    document.add(new Paragraph("CPF/CNPJ: " + venda.getClienteDocumento()).setFontSize(7));
                }

                document.add(new Paragraph("\nNFC-e nº " + venda.getNumeroNfce() + " Série " + venda.getSerieNfce() + " | Via Consumidor").setFontSize(7));
                document.add(new Paragraph("Protocolo de Autorização: " + (venda.getProtocolo() != null ? venda.getProtocolo() : "Aguardando")).setFontSize(7));
                document.add(new Paragraph("Data: " + venda.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).setFontSize(7));

                if (venda.getUrlQrCode() != null && !venda.getUrlQrCode().isBlank()) {
                    BarcodeQRCode qrCode = new BarcodeQRCode(venda.getUrlQrCode());
                    Image qrCodeImage = new Image(qrCode.createFormXObject(pdf));
                    qrCodeImage.setWidth(100f);
                    qrCodeImage.setHeight(100f);
                    qrCodeImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    document.add(qrCodeImage);
                }
            } else {
                document.add(new Paragraph("*** É VEDADA A AUTENTICAÇÃO DESTE CUPOM ***").setTextAlignment(TextAlignment.CENTER).setBold().setFontSize(7));
            }
            document.add(new Paragraph("DD Cosméticos System v1.0").setTextAlignment(TextAlignment.CENTER).setFontSize(6));

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erro gerando cupom", e);
            throw new RuntimeException("Erro ao gerar PDF do cupom.", e);
        }
    }

    // =========================================================================
    // 2. DANFE (NF-E) FORMATO A4 PARA EMPRESAS (B2B)
    // =========================================================================
    @Transactional(readOnly = true)
    public byte[] gerarDanfeA4(Long idVenda) {
        Venda venda = vendaRepository.findByIdComItens(idVenda)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada para impressão."));
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);
            Document document = new Document(pdf);
            document.setMargins(20, 20, 20, 20);

            // --- CABEÇALHO DANFE A4 ---
            Table header = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();

            Cell emitente = new Cell().setBorder(new DashedBorder(ColorConstants.GRAY, 1));
            emitente.add(new Paragraph(config.getLoja().getRazaoSocial()).setBold().setFontSize(12));
            emitente.add(new Paragraph("CNPJ: " + config.getLoja().getCnpj()).setFontSize(10));
            emitente.add(new Paragraph(config.getEndereco().getLogradouro() + ", " + config.getEndereco().getNumero()).setFontSize(9));
            emitente.add(new Paragraph(config.getEndereco().getBairro() + " - " + config.getEndereco().getCidade() + "/" + config.getEndereco().getUf()).setFontSize(9));
            header.addCell(emitente);

            Cell dadosNota = new Cell().setBorder(new DashedBorder(ColorConstants.GRAY, 1));
            dadosNota.add(new Paragraph("DANFE").setBold().setFontSize(16).setTextAlignment(TextAlignment.CENTER));
            dadosNota.add(new Paragraph("Documento Auxiliar da Nota Fiscal Eletrônica").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
            dadosNota.add(new Paragraph("Nº: " + venda.getNumeroNfce() + " | Série: " + venda.getSerieNfce()).setBold().setFontSize(11).setTextAlignment(TextAlignment.CENTER));

            String chaveStr = venda.getChaveAcessoNfce() != null ? venda.getChaveAcessoNfce() : "Aguardando Sefaz";
            dadosNota.add(new Paragraph("\nCHAVE DE ACESSO:\n" + chaveStr).setBold().setFontSize(10).setTextAlignment(TextAlignment.CENTER));
            header.addCell(dadosNota);

            document.add(header);

            // --- DESTINATÁRIO ---
            document.add(new Paragraph("\nDESTINATÁRIO / REMETENTE").setBold().setFontSize(10).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            Table dest = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            dest.addCell(new Cell().add(new Paragraph("NOME / RAZÃO SOCIAL: " + (venda.getClienteNome() != null ? venda.getClienteNome() : "Não Identificado"))).setFontSize(9));
            dest.addCell(new Cell().add(new Paragraph("CNPJ / CPF: " + (venda.getClienteDocumento() != null ? venda.getClienteDocumento() : "Não Identificado"))).setFontSize(9));
            document.add(dest);

            // --- TOTAIS ---
            document.add(new Paragraph("\nCÁLCULO DO IMPOSTO / TOTAIS").setBold().setFontSize(10).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            Table totais = new Table(UnitValue.createPercentArray(new float[]{33, 33, 34})).useAllAvailableWidth();
            totais.addCell(new Cell().add(new Paragraph("VALOR PRODUTOS:\nR$ " + venda.getValorTotal().add(venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO))).setFontSize(9));
            totais.addCell(new Cell().add(new Paragraph("DESCONTOS:\nR$ " + (venda.getDescontoTotal() != null ? venda.getDescontoTotal() : "0.00"))).setFontSize(9));
            totais.addCell(new Cell().add(new Paragraph("VALOR TOTAL DA NOTA:\nR$ " + venda.getValorTotal())).setBold().setFontSize(10));
            document.add(totais);

            // --- ITENS (DESCRIÇÃO E EAN COMPLETOS) ---
            document.add(new Paragraph("\nDADOS DOS PRODUTOS / SERVIÇOS").setBold().setFontSize(10).setBackgroundColor(ColorConstants.LIGHT_GRAY));
            Table tableItens = new Table(UnitValue.createPercentArray(new float[]{15, 45, 10, 10, 20})).useAllAvailableWidth();
            tableItens.addCell(new Cell().add(new Paragraph("CÓDIGO (EAN)").setFontSize(8).setBold()));
            tableItens.addCell(new Cell().add(new Paragraph("DESCRIÇÃO DO PRODUTO").setFontSize(8).setBold()));
            tableItens.addCell(new Cell().add(new Paragraph("QTD").setFontSize(8).setBold()));
            tableItens.addCell(new Cell().add(new Paragraph("UN").setFontSize(8).setBold()));
            tableItens.addCell(new Cell().add(new Paragraph("V. TOTAL").setFontSize(8).setBold()));

            for (ItemVenda item : venda.getItens()) {
                String ean = item.getProduto().getCodigoBarras() != null && !item.getProduto().getCodigoBarras().isBlank() ? item.getProduto().getCodigoBarras() : item.getProduto().getId().toString();
                BigDecimal totalItem = item.getPrecoUnitario().multiply(item.getQuantidade()).subtract(item.getDesconto() != null ? item.getDesconto() : BigDecimal.ZERO);

                tableItens.addCell(new Cell().add(new Paragraph(ean)).setFontSize(8));
                tableItens.addCell(new Cell().add(new Paragraph(item.getProduto().getDescricao())).setFontSize(8));
                tableItens.addCell(new Cell().add(new Paragraph(item.getQuantidade().setScale(0, RoundingMode.DOWN).toString())).setFontSize(8));
                tableItens.addCell(new Cell().add(new Paragraph("UN")).setFontSize(8));
                tableItens.addCell(new Cell().add(new Paragraph(totalItem.setScale(2, RoundingMode.HALF_UP).toString())).setFontSize(8));
            }
            document.add(tableItens);

            document.add(new Paragraph("\n* O arquivo XML oficial com validade jurídica encontra-se em anexo neste e-mail.").setFontSize(8));
            document.close();

            return out.toByteArray();
        } catch (Exception e) {
            log.error("Erro gerando DANFE", e);
            throw new RuntimeException("Erro ao gerar PDF da DANFE.", e);
        }
    }

    // Método de Etiqueta mantido intacto
    public String gerarEtiquetaTermica(Produto produto) {
        String nome = produto.getDescricao().length() > 25 ?
                produto.getDescricao().substring(0, 25) :
                produto.getDescricao();

        String preco = String.format("R$ %.2f", produto.getPrecoVenda());
        String codigo = produto.getCodigoBarras();

        return "^XA" +
                "^FO20,20^ADN,18,10^FD" + nome + "^FS" +
                "^FO20,50^A0N,40,40^FD" + preco + "^FS" +
                "^FO20,100^BY2" +
                "^BCN,50,Y,N,N" +
                "^FD" + codigo + "^FS" +
                "^XZ";
    }
}