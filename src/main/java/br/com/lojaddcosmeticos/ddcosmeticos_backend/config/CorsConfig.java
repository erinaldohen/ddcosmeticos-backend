package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE) // Força o CORS a ser validado antes da Segurança de Senhas (JWT)
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // 1. A MÁGICA DO IP DINÂMICO: Aceita Localhost E qualquer IP da sua rede (Wi-Fi local)
        config.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:[*]",
                "http://127.0.0.1:[*]",
                "http://192.168.*:[*]", // Redes Domésticas/Escritório Padrão
                "http://10.0.*:[*]",    // Redes Corporativas
                "http://172.16.*:[*]"   // Outras variações
        ));

        // 2. Permite todos os tipos de pedidos (Login, Apagar, Criar, etc.)
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 3. Permite que o React mande e receba o Token de Segurança (JWT) e os PDFs
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        config.setExposedHeaders(List.of("Content-Disposition")); // Crítico para permitir o download do Cupom PDF

        config.setAllowCredentials(true);

        // Aplica a regra a todo o sistema
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}