package br.com.lojaddcosmeticos.ddcosmeticos_backend.service.integracao;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoExternoDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            String urlCompleta = apiUrl + "/" + ean;

            ResponseEntity<String> response = restTemplate.exchange(
                    urlCompleta,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // DEBUG:
                System.out.println(">>> JSON RECEBIDO: " + response.getBody());

                JsonNode root = objectMapper.readTree(response.getBody());
                ProdutoExternoDTO dto = new ProdutoExternoDTO();
                dto.setEan(ean);

                // --- DESCRIÇÃO ---
                if (root.hasNonNull("description")) {
                    dto.setNome(root.get("description").asText());
                }

                // --- IMAGEM (Thumbnail ou Barcode) ---
                if (root.hasNonNull("thumbnail") && !root.get("thumbnail").asText().isEmpty()) {
                    dto.setUrlImagem(root.get("thumbnail").asText());
                } else if (root.hasNonNull("barcode_image")) {
                    // Fallback: Se não tem foto do produto, pega a imagem do código de barras
                    dto.setUrlImagem(root.get("barcode_image").asText());
                }

                // --- PREÇO ---
                if (root.hasNonNull("avg_price")) {
                    dto.setPrecoMedio(root.get("avg_price").asDouble());
                }

                // --- MARCA ---
                if (root.hasNonNull("brand")) {
                    JsonNode brandNode = root.get("brand");
                    if (brandNode.hasNonNull("name")) {
                        dto.setMarca(brandNode.get("name").asText());
                    }
                }

                // --- CATEGORIA (GPC) ---
                if (root.hasNonNull("gpc")) {
                    JsonNode gpcNode = root.get("gpc");
                    if (gpcNode.hasNonNull("description")) {
                        dto.setCategoria(gpcNode.get("description").asText());
                    }
                }

                // --- NCM ---
                if (root.hasNonNull("ncm")) {
                    JsonNode ncmNode = root.get("ncm");
                    if (ncmNode.hasNonNull("code")) {
                        dto.setNcm(ncmNode.get("code").asText().replace(".", ""));
                    }
                }

                // --- CEST ---
                if (root.hasNonNull("cest")) {
                    JsonNode cestNode = root.get("cest");
                    if (cestNode.hasNonNull("code")) {
                        dto.setCest(cestNode.get("code").asText().replace(".", ""));
                    }
                }

                // --- APLICA REGRAS FISCAIS ---
                aplicarRegrasDeNegocio(dto);

                return Optional.of(dto);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Erro na consulta Cosmos: " + e.getMessage());
        }

        return Optional.empty();
    }

    private void aplicarRegrasDeNegocio(ProdutoExternoDTO dto) {
        String ncm = dto.getNcm();

        // Se o NCM veio nulo da API (caso do seu produto Bell Corpus),
        // aplicamos os valores padrão do Simples Nacional para não deixar em branco.
        if (ncm == null || ncm.isEmpty()) {
            dto.setMonofasico(false);
            dto.setClassificacaoReforma("PADRAO");
            dto.setCst("102"); // Padrão Simples Nacional Tributado
            return;
        }

        String ncmLimpo = ncm.replace(".", "");

        // 1. REGRA MONOFÁSICO
        boolean isMonofasico =
                ncmLimpo.startsWith("3303") ||
                        ncmLimpo.startsWith("3304") ||
                        ncmLimpo.startsWith("3305") ||
                        ncmLimpo.startsWith("3307") ||
                        ncmLimpo.startsWith("3401") ||
                        ncmLimpo.startsWith("9619");

        dto.setMonofasico(isMonofasico);

        // 2. REGRA CST/CSOSN
        if (isMonofasico) {
            dto.setCst("500"); // ICMS cobrado anteriormente
        } else {
            dto.setCst("102"); // Tributado pelo Simples
        }

        // 3. REGRA REFORMA
        if (ncmLimpo.startsWith("9619")) {
            dto.setClassificacaoReforma("CESTA_BASICA");
        } else if (ncmLimpo.startsWith("3306") || ncmLimpo.startsWith("3401")) {
            dto.setClassificacaoReforma("REDUZIDA_60");
        } else {
            dto.setClassificacaoReforma("PADRAO");
        }
    }
}