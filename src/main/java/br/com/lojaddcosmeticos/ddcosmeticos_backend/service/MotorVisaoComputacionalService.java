package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao.OpenBeautyFactsService; // <-- Novo
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class MotorVisaoComputacionalService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private OpenBeautyFactsService openBeautyFactsService; // Zero Cost
    @Autowired private TesseractOcrService tesseractOcrService; // Zero Cost

    private final String DIRETORIO_IMAGENS = "uploads/produtos/";

    @Async
    @Transactional
    public void processarFilaDeImagensEmBackground(List<Long> idsProdutos) {
        log.info("🤖 MVC-F Open Source Iniciado...");

        try { Files.createDirectories(Paths.get(DIRETORIO_IMAGENS)); }
        catch (IOException e) { return; }

        for (Long id : idsProdutos) {
            Optional<Produto> produtoOpt = produtoRepository.findById(id);
            if (produtoOpt.isEmpty()) continue;
            Produto produto = produtoOpt.get();

            if (produto.getUrlImagem() != null && !produto.getUrlImagem().isEmpty() && !produto.getRevisaoImagemPendente()) continue;

            try {
                String urlEncontrada = buscarImagemLivre(produto);

                if (urlEncontrada != null) {
                    ProcessamentoImagemResult resultado = fazerDownloadEComprimir(urlEncontrada, produto.getCodigoBarras());

                    if (!produtoRepository.existsByHashImagem(resultado.hash)) {
                        produto.setUrlImagem(resultado.caminhoLocal);
                        produto.setHashImagem(resultado.hash);
                        produto.setRevisaoImagemPendente(resultado.precisaRevisaoHumana);
                        produtoRepository.save(produto);
                        log.info("📸 Imagem guardada na própria máquina: {}", produto.getDescricao());
                    }
                } else {
                    produto.setRevisaoImagemPendente(true);
                    produtoRepository.save(produto);
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                log.error("❌ Erro EAN " + produto.getCodigoBarras(), e);
            }
        }
    }

    private String buscarImagemLivre(Produto produto) {
        String ean = produto.getCodigoBarras();

        // 1ª Tentativa: Base de Dados Colaborativa (100% Free)
        if (ean != null && ean.length() >= 8 && !ean.startsWith("2")) {
            String urlDbLivre = openBeautyFactsService.buscarImagemPorEan(ean);
            if (urlDbLivre != null) return urlDbLivre;
        }

        // 2ª Tentativa: Scraper Google + Validação OCR (Ler Rótulo)
        return simularAgenteOcrWeb(produto);
    }

    private String simularAgenteOcrWeb(Produto produto) {
        log.info("🕸️ Buscando na web e validando com OCR local: {}", produto.getDescricao());
        String query = produto.getMarca() + " " + produto.getDescricao().replace(" ", "+") + "+fundo+branco";
        String searchUrl = "https://www.google.com/search?tbm=isch&q=" + query;

        try {
            Document doc = Jsoup.connect(searchUrl).userAgent("Mozilla/5.0").get();
            Elements images = doc.select("img[src~=(?i)\\.(png|jpe?g)]");

            int tentativas = 0;
            for (Element img : images) {
                if (tentativas >= 3) break;
                String imgUrl = img.attr("src");
                if (!imgUrl.startsWith("http") || imgUrl.contains("favicon")) continue;

                // O nosso Tesseract entra em ação
                if (tesseractOcrService.auditarImagemLocalmente(imgUrl, produto.getDescricao())) {
                    return imgUrl;
                }
                tentativas++;
            }
        } catch (Exception e) {
            log.error("Erro no Scraper OS", e);
        }
        return null;
    }

    private ProcessamentoImagemResult fazerDownloadEComprimir(String urlOrigem, String ean) throws Exception {
        URL url = new URL(urlOrigem);
        String nomeFicheiro = (ean != null && !ean.isEmpty() ? ean : UUID.randomUUID().toString()) + ".webp";
        Path caminhoDestino = Paths.get(DIRETORIO_IMAGENS, nomeFicheiro);
        log.info("📂 TENTANDO SALVAR IMAGEM EM: {}", caminhoDestino.toAbsolutePath().toString());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = url.openStream()) {
            Thumbnails.of(is).size(800, 800).outputFormat("webp").outputQuality(0.8).toOutputStream(baos);
        }

        byte[] imagemBytes = baos.toByteArray();
        Files.write(caminhoDestino, imagemBytes);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(imagemBytes);
        StringBuilder hashHex = new StringBuilder();
        for (byte b : hashBytes) { hashHex.append(String.format("%02x", b)); }

        ProcessamentoImagemResult result = new ProcessamentoImagemResult();
        result.caminhoLocal = "/uploads/produtos/" + nomeFicheiro;
        result.hash = hashHex.toString();
        result.precisaRevisaoHumana = false;
        return result;
    }

    private static class ProcessamentoImagemResult {
        String caminhoLocal; String hash; boolean precisaRevisaoHumana;
    }
}