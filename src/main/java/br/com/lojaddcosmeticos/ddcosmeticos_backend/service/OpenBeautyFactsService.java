package br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class OpenBeautyFactsService {

    private final RestTemplate restTemplate = new RestTemplate();

    // API Livre, Mundial e Aberta para Cosméticos
    private final String BASE_URL = "https://world.openbeautyfacts.org/api/v0/product/";

    public String buscarImagemPorEan(String ean) {
        log.info("🌐 Consultando Catálogo Open Source (OpenBeautyFacts) para EAN: {}", ean);
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(BASE_URL + ean + ".json", JsonNode.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = response.getBody();
                if (root.has("status") && root.get("status").asInt() == 1) {
                    JsonNode product = root.get("product");
                    if (product.has("image_url")) {
                        String imageUrl = product.get("image_url").asText();
                        log.info("✅ Imagem encontrada na base livre: {}", imageUrl);
                        return imageUrl;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ EAN {} não encontrado na base livre.", ean);
        }
        return null;
    }
}