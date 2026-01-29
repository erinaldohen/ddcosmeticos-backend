package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.handler.SecurityFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    @Autowired
    private SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. ROTAS PÚBLICAS
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**", "/auth/**").permitAll()
                        .requestMatchers("/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**", "/uploads/**").permitAll() // Adicionado uploads

                        // 2. LEITURA E OPERACIONAL (Liberado para qualquer utilizador logado)
                        .requestMatchers(HttpMethod.GET, "/api/v1/produtos/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/caixa/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auditoria/eventos").authenticated()

                        // Operações de Venda e Caixa
                        .requestMatchers(HttpMethod.POST, "/api/v1/vendas/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/caixa/**").authenticated()

                        // 3. RESTRITO A ADMIN (Escrita e Gestão)
                        // Configurações da Loja (Importante para o seu caso agora)
                        .requestMatchers("/api/v1/configuracoes/**").hasRole("ADMIN")

                        // Produtos
                        .requestMatchers(HttpMethod.POST, "/api/v1/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/produtos/**").hasRole("ADMIN")

                        // Áreas Sensíveis
                        .requestMatchers("/api/v1/dashboard/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/fiscal/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/auditoria/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/fornecedores/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN")

                        // 4. RESTO (Bloqueio padrão)
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // --- CORREÇÃO DO ERRO DE CORS ---
        // Usamos APENAS setAllowedOriginPatterns("*") quando Credentials é true.
        // Removemos setAllowedOrigins para evitar conflito.
        configuration.setAllowedOriginPatterns(List.of("*"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization")); // Permite o front ler headers de resposta
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}