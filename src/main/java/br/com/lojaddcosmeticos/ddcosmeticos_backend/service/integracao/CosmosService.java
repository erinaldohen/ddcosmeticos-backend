package br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoExternoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CalculadoraFiscalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class CosmosService {

    @Value("${cosmos.api.url}")
    private String apiUrl;

    @Value("${cosmos.api.token}")
    private String apiToken;

    @Autowired
    private CalculadoraFiscalService calculadoraFiscalService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<ProdutoExternoDTO> consultarEan(String ean) {
        if (apiToken == null || apiToken.isEmpty() || "${COSMOS_TOKEN}".equals(apiToken)) {
            System.err.println("❌ Erro: Token do Cosmos não configurado corretamente.");
            return Optional.empty();
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Cosmos-Token", apiToken);
            headers.set("User-Agent", "Cosmos-API-Request");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String baseUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
            String urlCompleta = baseUrl + "/" + ean;

            ResponseEntity<String> response = restTemplate.exchange(
                    urlCompleta, HttpMethod.GET, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                ProdutoExternoDTO dto = new ProdutoExternoDTO();
                dto.setEan(ean);

                // Mapeamento Básico
                if (root.hasNonNull("description")) dto.setNome(root.get("description").asText());
                if (root.hasNonNull("thumbnail") && !root.get("thumbnail").asText().isEmpty()) {
                    dto.setUrlImagem(root.get("thumbnail").asText());
                } else if (root.hasNonNull("barcode_image")) {
                    dto.setUrlImagem(root.get("barcode_image").asText());
                }
                if (root.hasNonNull("avg_price")) dto.setPrecoMedio(root.get("avg_price").asDouble());
                if (root.hasNonNull("brand") && root.get("brand").hasNonNull("name")) {
                    dto.setMarca(root.get("brand").get("name").asText());
                }
                if (root.hasNonNull("gpc") && root.get("gpc").hasNonNull("description")) {
                    dto.setCategoria(root.get("gpc").get("description").asText());
                }
                if (root.hasNonNull("ncm") && root.get("ncm").hasNonNull("code")) {
                    dto.setNcm(root.get("ncm").get("code").asText().replace(".", ""));
                }
                if (root.hasNonNull("cest") && root.get("cest").hasNonNull("code")) {
                    dto.setCest(root.get("cest").get("code").asText().replace(".", ""));
                }

                // INTELIGÊNCIA FISCAL CENTRALIZADA
                calculadoraFiscalService.aplicarRegras(dto);

                return Optional.of(dto);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Erro na consulta Cosmos: " + e.getMessage());
        }
        return Optional.empty();
    }
}