package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "DD Cosméticos API",
                version = "v1",
                description = "Documentação dos endpoints do ERP"
        )
)
@SecurityScheme(
        name = "bearerAuth", // Nome do esquema de segurança
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class OpenApiConfig {
    // Nenhuma lógica necessária, as anotações fazem tudo
}