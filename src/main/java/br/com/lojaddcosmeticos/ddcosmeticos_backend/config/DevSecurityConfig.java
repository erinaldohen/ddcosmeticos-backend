// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/config/DevSecurityConfig.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de Segurança específica para o perfil 'dev'.
 * O objetivo é desabilitar a proteção básica (CSRF e FrameOptions) para facilitar o desenvolvimento
 * e permitir o acesso a todas as APIs e ferramentas como o H2 Console.
 * Esta configuração é insegura e deve ser usada APENAS em ambiente de desenvolvimento.
 */
@Configuration
@Profile("dev")
public class DevSecurityConfig {

    /**
     * Define a cadeia de filtros de segurança para o ambiente de desenvolvimento.
     *
     * @param http Objeto HttpSecurity para configurar a segurança.
     * @return Uma cadeia de filtros de segurança configurada.
     * @throws Exception Se ocorrer um erro na configuração.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 1. Desabilita o CSRF (Cross-Site Request Forgery): Essencial para testes de API REST e ferramentas como H2 Console.
                .csrf(csrf -> csrf.disable())

                // 2. Permite que a aplicação seja aberta em um frame (iframe): Essencial para o H2 Console rodar internamente (frame-options: sameOrigin).
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))

                // 3. Autoriza todas as requisições: Libera o acesso a qualquer URL (incluindo /api/v1/** e /h2-console).
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll() // Permite todas as requisições no perfil 'dev'.
                );

        return http.build();
    }
}