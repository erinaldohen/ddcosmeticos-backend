// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/config/WebSecurityConfig.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração principal do Spring Security. 
 * Define o filtro JWT, as políticas de sessão e o acesso por endpoint.
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Autowired
    private SecurityFilter securityFilter;

    /**
     * Define as regras de segurança para as requisições HTTP.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Desativa CSRF (necessário para APIs REST)
                .csrf(AbstractHttpConfigurer::disable)

                // Configura política de sessão como STATELESS (Sem estado), essencial para JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Define as permissões de acesso aos endpoints
                .authorizeHttpRequests(authorize -> authorize
                        // 1. Libera o endpoint de Login (POST)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()

                        // 2. Libera o acesso ao Console H2 (somente para desenvolvimento)
                        .requestMatchers("/h2-console/**").permitAll()

                        // 3. Libera a consulta de Produtos (PDV não precisa de login para consultar preço)
                        .requestMatchers(HttpMethod.GET, "/api/v1/produtos").permitAll()

                        // 4. Requer permissão de CAIXA para registrar Vendas
                        .requestMatchers(HttpMethod.POST, "/api/v1/vendas").hasAnyRole("CAIXA", "GERENTE")

                        // 5. Requer permissão de GERENTE para entradas de Custo/NF
                        .requestMatchers(HttpMethod.POST, "/api/v1/custo/entrada").hasRole("GERENTE")

                        // 6. Todas as outras requisições exigem autenticação
                        .anyRequest().authenticated()
                )
                // Permite o acesso ao console H2 em iframes (Necessário para Dev)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))

                // Adiciona o filtro JWT antes do filtro de autenticação padrão do Spring
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Expõe o AuthenticationManager como um Bean para ser injetado no AuthenticationController.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}