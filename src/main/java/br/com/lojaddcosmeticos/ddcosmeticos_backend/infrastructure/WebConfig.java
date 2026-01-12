package br.com.lojaddcosmeticos.ddcosmeticos_backend.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Mapeia a URL /imagens/** para a pasta física uploads/ na raiz do projeto
        registry.addResourceHandler("/imagens/**")
                .addResourceLocations("file:./uploads/");
        // O "file:./" é importante para indicar que é no sistema de arquivos, relativo à raiz
    }
}