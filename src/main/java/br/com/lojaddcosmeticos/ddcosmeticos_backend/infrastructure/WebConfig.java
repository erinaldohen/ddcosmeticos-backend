package br.com.lojaddcosmeticos.ddcosmeticos_backend.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Pega o caminho absoluto da pasta "uploads" na raiz do projeto
        Path uploadDir = Paths.get("./uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();

        // TRATAMENTO PARA WINDOWS (Crucial)
        // 1. Substitui barras invertidas (\) por barras normais (/)
        uploadPath = uploadPath.replace("\\", "/");

        // 2. Garante que termina com barra, senão o Spring não lê a pasta
        if (!uploadPath.endsWith("/")) {
            uploadPath += "/";
        }

        // 3. Adiciona o prefixo de sistema de arquivos
        String location = "file:///" + uploadPath;

        System.out.println("----------------------------------------------------");
        System.out.println("📂 MAPEANDO IMAGENS");
        System.out.println("📍 URL Externa: /uploads/**");
        System.out.println("📍 Pasta Local: " + location);
        System.out.println("----------------------------------------------------");

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:/" + uploadPath + "/");
    }
}