// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/config/CorsConfig.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração global do CORS (Cross-Origin Resource Sharing).
 * Essencial para permitir que o Frontend (React/PWA) acesse a API REST (Backend).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * Define as regras de CORS, permitindo requisições de origens específicas.
     * @param registry O registro de CORS.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Mapeia todos os endpoints da API (v1)
        registry.addMapping("/api/v1/**")
                // Permite origens comuns de desenvolvimento de frontend (React, Vite, etc.)
                .allowedOrigins("http://localhost:3000", "http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Permite os métodos essenciais
                .allowedHeaders("*") // Permite todos os headers
                .allowCredentials(true); // Permite cookies e headers de autorização
    }
}