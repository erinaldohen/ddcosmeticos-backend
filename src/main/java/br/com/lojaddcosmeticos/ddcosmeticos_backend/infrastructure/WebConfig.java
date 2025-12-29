package br.com.lojaddcosmeticos.ddcosmeticos_backend.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração para servir arquivos estáticos (Imagens).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mapeia qualquer requisição que comece com "/imagens/" para a pasta "uploads" na raiz do projeto.
        // O prefixo "file:" indica que é um caminho no sistema de arquivos local.
        registry.addResourceHandler("/imagens/**")
                .addResourceLocations("file:uploads/");
    }
}