package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentoFiscalPdfService {

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    // Paleta de Cores Premium para o PDF
    private final DeviceRgb HEADER_BG = new DeviceRgb(15, 23, 42); // Escuro profundo
    private final DeviceRgb HEADER_TEXT = new DeviceRgb(255, 255, 255); // Branco
    private final DeviceRgb LIGHT_BG = new DeviceRgb(241, 245, 249); // Cinza super claro

    // ==========================================================
    // UTILITÁRIOS DE FORMATAÇÃO
    // ==========================================================

    private String formatarCnpjCpf(String documento) {
        if (documento == null || documento.isBlank()) return "Não Informado";
        String apenasNumeros = documento.replaceAll("\\D", "");

        if (apenasNumeros.length() == 14) {
            return apenasNumeros.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        } else if (apenasNumeros.length() == 11) {
            return apenasNumeros.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        }
        return documento; // Retorna original se o tamanho for atípico
    }

    private String formatarMoeda(BigDecimal valor) {
        if (valor == null) valor = BigDecimal.ZERO;
        NumberFormat formatoMoeda = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return formatoMoeda.format(valor);
    }

    // ==========================================================
    // 1. GERADOR DE CUPOM FISCAL (NFC-E) - FORMATO TÉRMICO 80mm
    // ==========================================================
    public byte[] gerarCupomNfce(Venda venda, ConfiguracaoLoja config) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);

        pdf.setDefaultPageSize(new PageSize(226f, 1000f));
        Document document = new Document(pdf);
        document.setMargins(10, 10, 10, 10);

        document.add(new Paragraph(config.getLoja().getRazaoSocial()).setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(9));
        document.add(new Paragraph("CNPJ: " + formatarCnpjCpf(config.getLoja().getCnpj())).setTextAlignment(TextAlignment.CENTER).setFontSize(7));
        document.add(new Paragraph(config.getEndereco().getLogradouro() + ", " + config.getEndereco().getNumero() + " - " + config.getEndereco().getCidade()).setTextAlignment(TextAlignment.CENTER).setFontSize(7));

        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));
        document.add(new Paragraph("Documento Auxiliar da Nota Fiscal de Consumidor Eletrônica").setBold().setTextAlignment(TextAlignment.CENTER).setFontSize(7));
        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

        Table table = new Table(UnitValue.createPercentArray(new float[]{45, 15, 15, 25})).useAllAvailableWidth();
        table.addCell(new Cell().add(new Paragraph("DESC")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
        table.addCell(new Cell().add(new Paragraph("QTD")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
        table.addCell(new Cell().add(new Paragraph("UN")).setBorder(Border.NO_BORDER).setFontSize(7).setBold());
        table.addCell(new Cell().add(new Paragraph("VL TOT")).setBorder(Border.NO_BORDER).setFontSize(7).setBold().setTextAlignment(TextAlignment.RIGHT));

        for (ItemVenda item : venda.getItens()) {
            table.addCell(new Cell().add(new Paragraph(item.getProduto().getDescricao())).setBorder(Border.NO_BORDER).setFontSize(6));
            table.addCell(new Cell().add(new Paragraph(item.getQuantidade().setScale(0, RoundingMode.DOWN).toString())).setBorder(Border.NO_BORDER).setFontSize(6));
            table.addCell(new Cell().add(new Paragraph("UN")).setBorder(Border.NO_BORDER).setFontSize(6));
            table.addCell(new Cell().add(new Paragraph(formatarMoeda(item.getPrecoUnitario().multiply(item.getQuantidade())))).setBorder(Border.NO_BORDER).setFontSize(6).setTextAlignment(TextAlignment.RIGHT));
        }
        document.add(table);

        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

        document.add(new Paragraph("QTD TOTAL DE ITENS: " + venda.getItens().size()).setFontSize(7));
        if (venda.getDescontoTotal().compareTo(java.math.BigDecimal.ZERO) > 0) {
            document.add(new Paragraph("DESCONTOS: " + formatarMoeda(venda.getDescontoTotal())).setFontSize(7));
        }
        document.add(new Paragraph("VALOR TOTAL: " + formatarMoeda(venda.getValorTotal())).setBold().setFontSize(9));
        document.add(new Paragraph("FORMA PAGAMENTO: " + venda.getFormaDePagamento()).setFontSize(7));

        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

        document.add(new Paragraph("Consumidor: " + (venda.getClienteNome() != null ? venda.getClienteNome() : "Consumidor Final")).setFontSize(7));
        document.add(new Paragraph("CPF/CNPJ: " + formatarCnpjCpf(venda.getClienteDocumento())).setFontSize(7));
        document.add(new Paragraph("Emissão: " + venda.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + " | Num: " + venda.getNumeroNfce() + " | Série: " + venda.getSerieNfce()).setFontSize(7));

        document.add(new Paragraph("--------------------------------------------------").setTextAlignment(TextAlignment.CENTER).setFontSize(8));

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

        pdf.setDefaultPageSize(PageSize.A4);
        Document document = new Document(pdf);
        document.setMargins(20, 20, 20, 20);

        Table header = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
        Cell emitente = new Cell().add(new Paragraph(config.getLoja().getRazaoSocial()).setBold().setFontSize(12));
        emitente.add(new Paragraph("CNPJ: " + formatarCnpjCpf(config.getLoja().getCnpj())).setFontSize(10));
        emitente.add(new Paragraph(config.getEndereco().getLogradouro() + ", " + config.getEndereco().getNumero()).setFontSize(9));
        header.addCell(emitente);

        Cell dadosNota = new Cell().add(new Paragraph("DANFE").setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER));
        dadosNota.add(new Paragraph("Documento Auxiliar da Nota Fiscal Eletrônica").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        dadosNota.add(new Paragraph("Nº: " + venda.getNumeroNfce() + " | Série: " + venda.getSerieNfce()).setBold().setFontSize(11).setTextAlignment(TextAlignment.CENTER));

        String chaveStr = venda.getChaveAcessoNfce() != null ? venda.getChaveAcessoNfce() : "Aguardando Sefaz";
        dadosNota.add(new Paragraph("CHAVE DE ACESSO:\n" + chaveStr).setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        header.addCell(dadosNota);

        document.add(header);

        document.add(new Paragraph("\nDESTINATÁRIO / REMETENTE").setBold().setFontSize(10).setBackgroundColor(LIGHT_BG).setPadding(3));
        Table dest = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        dest.addCell(new Cell().add(new Paragraph("NOME / RAZÃO SOCIAL: " + venda.getClienteNome()).setFontSize(9)).setPadding(5));
        dest.addCell(new Cell().add(new Paragraph("CNPJ / CPF: " + formatarCnpjCpf(venda.getClienteDocumento())).setFontSize(9)).setPadding(5));
        document.add(dest);

        document.add(new Paragraph("\nCÁLCULO DO IMPOSTO / TOTAIS").setBold().setFontSize(10).setBackgroundColor(LIGHT_BG).setPadding(3));
        Table totais = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
        totais.addCell(new Cell().add(new Paragraph("VALOR DOS PRODUTOS: " + formatarMoeda(venda.getValorTotal().add(venda.getDescontoTotal()))).setFontSize(9)).setPadding(5));
        totais.addCell(new Cell().add(new Paragraph("VALOR TOTAL DA NOTA: " + formatarMoeda(venda.getValorTotal())).setBold().setFontSize(10)).setPadding(5));
        document.add(totais);

        document.add(new Paragraph("\nDADOS DOS PRODUTOS / SERVIÇOS").setBold().setFontSize(10).setBackgroundColor(LIGHT_BG).setPadding(3));
        Table tableItens = new Table(UnitValue.createPercentArray(new float[]{15, 45, 10, 10, 20})).useAllAvailableWidth();
        tableItens.addCell(new Cell().add(new Paragraph("CÓDIGO").setFontSize(8).setBold()).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT));
        tableItens.addCell(new Cell().add(new Paragraph("DESCRIÇÃO").setFontSize(8).setBold()).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT));
        tableItens.addCell(new Cell().add(new Paragraph("QTD").setFontSize(8).setBold()).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT));
        tableItens.addCell(new Cell().add(new Paragraph("UN").setFontSize(8).setBold()).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT));
        tableItens.addCell(new Cell().add(new Paragraph("V. TOTAL").setFontSize(8).setBold()).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT));

        for (ItemVenda item : venda.getItens()) {
            tableItens.addCell(new Cell().add(new Paragraph(item.getProduto().getCodigoBarras() != null ? item.getProduto().getCodigoBarras() : item.getProduto().getId().toString())).setFontSize(8));
            tableItens.addCell(new Cell().add(new Paragraph(item.getProduto().getDescricao())).setFontSize(8));
            tableItens.addCell(new Cell().add(new Paragraph(item.getQuantidade().setScale(2, RoundingMode.HALF_UP).toString())).setFontSize(8).setTextAlignment(TextAlignment.CENTER));
            tableItens.addCell(new Cell().add(new Paragraph("UN")).setFontSize(8).setTextAlignment(TextAlignment.CENTER));
            tableItens.addCell(new Cell().add(new Paragraph(formatarMoeda(item.getPrecoUnitario().multiply(item.getQuantidade())))).setFontSize(8).setTextAlignment(TextAlignment.RIGHT));
        }
        document.add(tableItens);

        document.close();
        return out.toByteArray();
    }

    // ==========================================================
    // 3. GERADOR DE DANFE ESPELHO (ENTRADA) - APRIMORADO
    // ==========================================================
    public byte[] gerarDanfePdf(String numeroNota) {

        List<MovimentoEstoque> itens = movimentoEstoqueRepository.buscarItensDaNota(numeroNota);

        if (itens == null || itens.isEmpty()) {
            throw new RuntimeException("Nenhum item encontrado para a nota: " + numeroNota);
        }

        MovimentoEstoque primeiroItem = itens.get(0);
        String fornecedorNome = primeiroItem.getFornecedor() != null ? primeiroItem.getFornecedor().getNomeFantasia() : "Fornecedor Não Informado";

        // Aplica a máscara no CNPJ
        String fornecedorCnpj = primeiroItem.getFornecedor() != null ? formatarCnpjCpf(primeiroItem.getFornecedor().getCnpj()) : "---";
        String dataEntrada = primeiroItem.getDataMovimento() != null ? primeiroItem.getDataMovimento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "---";

        BigDecimal valorTotalNota = itens.stream()
                .map(i -> i.getCustoMovimentado().multiply(i.getQuantidadeMovimentada()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4);
            Document document = new Document(pdf);
            document.setMargins(30, 30, 30, 30);

            // --- BLOCO 1: CABEÇALHO ---
            Table header = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            header.setBorder(new SolidBorder(ColorConstants.GRAY, 1));

            Cell emitente = new Cell().add(new Paragraph("DD COSMÉTICOS").setBold().setFontSize(16));
            emitente.add(new Paragraph("Rastreabilidade Interna / Auditoria").setFontSize(9).setFontColor(ColorConstants.DARK_GRAY));
            emitente.add(new Paragraph("DOCUMENTO AUXILIAR DE ENTRADA").setBold().setFontSize(10));
            emitente.setPadding(10);
            header.addCell(emitente);

            Cell dadosNota = new Cell().add(new Paragraph("DANFE - ESPELHO").setBold().setFontSize(16).setTextAlignment(TextAlignment.CENTER));
            dadosNota.add(new Paragraph("Nº: " + numeroNota + " | Série: 1").setBold().setFontSize(12).setTextAlignment(TextAlignment.CENTER));
            dadosNota.add(new Paragraph("Data de Processamento no Sistema:\n" + dataEntrada).setFontSize(9).setTextAlignment(TextAlignment.CENTER));
            dadosNota.setPadding(10);
            header.addCell(dadosNota);

            document.add(header);
            document.add(new Paragraph("\n")); // Espaçamento

            // --- BLOCO 2: DADOS DO FORNECEDOR ---
            Cell headerForn = new Cell().add(new Paragraph("DADOS DO FORNECEDOR / REMETENTE").setBold().setFontSize(10).setFontColor(HEADER_TEXT));
            headerForn.setBackgroundColor(HEADER_BG).setPadding(4);

            Table tableFornecedor = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            tableFornecedor.addHeaderCell(headerForn);

            Table fornInner = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            fornInner.addCell(new Cell().add(new Paragraph("RAZÃO SOCIAL:\n" + fornecedorNome).setFontSize(9)).setPadding(5));
            fornInner.addCell(new Cell().add(new Paragraph("CNPJ:\n" + fornecedorCnpj).setFontSize(9)).setPadding(5)); // CNPJ Mascarado
            document.add(fornInner);

            document.add(new Paragraph("\n"));

            // --- BLOCO 3: CÁLCULO E TOTAIS ---
            Cell headerTotais = new Cell().add(new Paragraph("RESUMO E TOTAIS DA OPERAÇÃO").setBold().setFontSize(10).setFontColor(HEADER_TEXT));
            headerTotais.setBackgroundColor(HEADER_BG).setPadding(4);

            Table tableTotais = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            tableTotais.addHeaderCell(headerTotais);

            Table totaisInner = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            totaisInner.addCell(new Cell().add(new Paragraph("QUANTIDADE TOTAL DE ITENS FÍSICOS:\n" + itens.size() + " Volumes").setFontSize(9)).setPadding(5));
            totaisInner.addCell(new Cell().add(new Paragraph("VALOR TOTAL DA NOTA:\n" + formatarMoeda(valorTotalNota)).setBold().setFontSize(11)).setPadding(5)); // Moeda BRL
            document.add(totaisInner);

            document.add(new Paragraph("\n"));

            // --- BLOCO 4: TABELA DE ITENS DA NOTA ---
            document.add(new Paragraph("RELAÇÃO DE PRODUTOS IMPORTADOS").setBold().setFontSize(10).setBackgroundColor(LIGHT_BG).setPadding(4));

            Table tableItens = new Table(UnitValue.createPercentArray(new float[]{15, 45, 10, 15, 15})).useAllAvailableWidth();

            // Cabeçalho da Tabela
            tableItens.addHeaderCell(new Cell().add(new Paragraph("CÓDIGO").setFontSize(8).setBold()).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT).setPadding(4));
            tableItens.addHeaderCell(new Cell().add(new Paragraph("DESCRIÇÃO DO PRODUTO").setFontSize(8).setBold()).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT).setPadding(4));
            tableItens.addHeaderCell(new Cell().add(new Paragraph("QTD").setFontSize(8).setBold().setTextAlignment(TextAlignment.CENTER)).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT).setPadding(4));
            tableItens.addHeaderCell(new Cell().add(new Paragraph("V. UNIT").setFontSize(8).setBold().setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT).setPadding(4));
            tableItens.addHeaderCell(new Cell().add(new Paragraph("V. TOTAL").setFontSize(8).setBold().setTextAlignment(TextAlignment.RIGHT)).setBackgroundColor(HEADER_BG).setFontColor(HEADER_TEXT).setPadding(4));

            // Loop para preencher os itens reais
            for (MovimentoEstoque item : itens) {
                String codigo = item.getProduto() != null ? String.valueOf(item.getProduto().getId()) : "N/A";
                String descricao = item.getProduto() != null ? item.getProduto().getDescricao() : "Produto Indefinido";
                String qtd = String.valueOf(item.getQuantidadeMovimentada());

                // Formatação Monetária correta nos itens da DANFE
                String vUnit = formatarMoeda(item.getCustoMovimentado());
                String vTot = formatarMoeda(item.getCustoMovimentado().multiply(item.getQuantidadeMovimentada()));

                tableItens.addCell(new Cell().add(new Paragraph(codigo).setFontSize(8)).setPadding(3));
                tableItens.addCell(new Cell().add(new Paragraph(descricao).setFontSize(8)).setPadding(3));
                tableItens.addCell(new Cell().add(new Paragraph(qtd).setFontSize(8).setTextAlignment(TextAlignment.CENTER)).setPadding(3));
                tableItens.addCell(new Cell().add(new Paragraph(vUnit).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setPadding(3));
                tableItens.addCell(new Cell().add(new Paragraph(vTot).setFontSize(8).setTextAlignment(TextAlignment.RIGHT)).setPadding(3));
            }
            document.add(tableItens);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao desenhar PDF da DANFE", e);
        }
    }
}