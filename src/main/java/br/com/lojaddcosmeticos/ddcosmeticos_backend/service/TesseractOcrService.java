package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;

@Slf4j
@Service
public class TesseractOcrService {

    private final Tesseract tesseract;

    public TesseractOcrService() {
        this.tesseract = new Tesseract();
        // O Tesseract precisa saber onde estão os ficheiros de idioma (ex: por, eng)
        // Você terá de baixar o ficheiro 'por.traineddata' e colocar nesta pasta:
        this.tesseract.setDatapath("src/main/resources/tessdata");
        this.tesseract.setLanguage("por"); // Português
    }

    /**
     * Extrai o texto da imagem e verifica se bate com a Nota Fiscal
     */
    public boolean auditarImagemLocalmente(String imageUrl, String descricaoNotaFiscal) {
        log.info("👁️ Lendo rótulo da imagem com OCR Open Source...");
        try {
            // Baixa a imagem temporariamente para a memória
            URL url = new URL(imageUrl);
            BufferedImage image = ImageIO.read(url);

            // A MÁGICA: Extrai todo o texto da embalagem
            String textoExtraido = tesseract.doOCR(image).toUpperCase();

            String descUpper = descricaoNotaFiscal.toUpperCase();

            // Lógica de Match Heurístico (Extremamente rápido e gratuito)
            // Extrai a principal palavra (ex: PANTENE) e a volumetria (ex: 400ML)
            String[] palavrasChave = descUpper.split(" ");

            int matches = 0;
            for (String palavra : palavrasChave) {
                if (palavra.length() > 3 && textoExtraido.contains(palavra)) {
                    matches++;
                }
            }

            // Se pelo menos 2 palavras cruciais baterem, aprova!
            boolean aprovada = matches >= 2 ||
                    (descUpper.contains("ML") && textoExtraido.contains(extrairVolume(descUpper)));

            log.info("⚖️ OCR Veredito: {}. Palavras detectadas na embalagem: {}",
                    (aprovada ? "APROVADA" : "REJEITADA"), matches);

            return aprovada;

        } catch (Exception e) {
            log.error("❌ Erro no motor de OCR. Rejeitando por segurança.", e);
            return false;
        }
    }

    private String extrairVolume(String descricao) {
        // Lógica simples para achar "400ML", "200G", etc.
        return descricao.replaceAll(".*\\b(\\d{2,4}(ML|G|KG|L))\\b.*", "$1");
    }
}