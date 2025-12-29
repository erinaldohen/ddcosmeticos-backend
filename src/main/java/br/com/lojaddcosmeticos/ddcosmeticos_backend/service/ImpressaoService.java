package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Serviço responsável pela geração de documentos PDF (Cupons Fiscais).
 */
@Service
public class ImpressaoService {

    @Autowired
    private VendaRepository vendaRepository;

    /**
     * Gera um PDF formatado para impressoras térmicas (80mm) ou visualização em tela.
     * @param idVenda ID da venda a ser impressa.
     * @return Array de bytes contendo o arquivo PDF.
     */
    public byte[] gerarCupomNfce(Long idVenda) {
        Venda venda = vendaRepository.findByIdComItens(idVenda)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada para impressão."));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Configuração da Página: Tamanho A7 (próximo de cupom térmico), margens pequenas
            Document document = new Document(PageSize.A7, 10, 10, 10, 10);
            PdfWriter.getInstance(document, out);
            document.open();

            // Definição de Fontes
            Font fontBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font fontNormal = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font fontSmall = FontFactory.getFont(FontFactory.COURIER, 7);

            // --- CABEÇALHO ---
            document.add(new Paragraph("DD COSMETICOS LTDA", fontBold));
            document.add(new Paragraph("CNPJ: 57.648.950/0001-44", fontNormal));
            document.add(new Paragraph("Rua Exemplo, 123 - Centro, Recife/PE", fontNormal));
            document.add(new Paragraph("--------------------------------", fontNormal));
            document.add(new Paragraph("DANFE NFC-e - Documento Auxiliar", fontBold));
            document.add(new Paragraph("Não permite aproveitamento de crédito de ICMS", fontSmall));
            document.add(new Paragraph("--------------------------------", fontNormal));

            // --- LISTA DE ITENS ---
            document.add(new Paragraph("ITEM  CODIGO      DESC.      QTD  VALOR", fontNormal));

            int i = 1;
            for (ItemVenda item : venda.getItens()) {
                String descricaoLimitada = item.getProduto().getDescricao();
                if (descricaoLimitada.length() > 20) descricaoLimitada = descricaoLimitada.substring(0, 20);

                String linha = String.format("%03d %s %s %.0f x %.2f",
                        i++,
                        item.getProduto().getCodigoBarras().substring(0, Math.min(6, item.getProduto().getCodigoBarras().length())),
                        descricaoLimitada,
                        item.getQuantidade().doubleValue(),
                        item.getPrecoUnitario().doubleValue());

                document.add(new Paragraph(linha, fontSmall));
            }
            document.add(new Paragraph("--------------------------------", fontNormal));

            // --- TOTAIS ---
            document.add(new Paragraph(String.format("QTD TOTAL DE ITENS: %d", venda.getItens().size()), fontNormal));
            document.add(new Paragraph(String.format("VALOR TOTAL R$: %.2f", venda.getTotalVenda()), fontBold));

            if (venda.getDescontoTotal() != null && venda.getDescontoTotal().compareTo(BigDecimal.ZERO) > 0) {
                document.add(new Paragraph(String.format("DESCONTOS R$: -%.2f", venda.getDescontoTotal()), fontNormal));
            }

            BigDecimal valorPagar = venda.getTotalVenda().subtract(venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO);
            document.add(new Paragraph(String.format("VALOR A PAGAR R$: %.2f", valorPagar), fontBold));
            document.add(new Paragraph("FORMA PAGAMENTO: " + venda.getFormaPagamento(), fontNormal));
            document.add(new Paragraph("--------------------------------", fontNormal));

            // --- RODAPÉ FISCAL (DADOS DA SEFAZ) ---
            document.add(new Paragraph("EMISSÃO: " + venda.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")), fontNormal));

            if (venda.getXmlNfce() != null) {
                document.add(new Paragraph("CHAVE DE ACESSO:", fontNormal));
                // Chave simulada para layout (a real estaria no XML)
                document.add(new Paragraph("43250157648950000144550010000057141000000000", fontSmall));
                document.add(new Paragraph("\nCONSULTE PELA CHAVE DE ACESSO EM:", fontSmall));
                document.add(new Paragraph("http://www.sefaz.rs.gov.br/nfce", fontSmall));
            } else {
                document.add(new Paragraph("NOTA EM CONTINGÊNCIA / NÃO TRANSMITIDA", fontBold));
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF do cupom.", e);
        }
    }
}