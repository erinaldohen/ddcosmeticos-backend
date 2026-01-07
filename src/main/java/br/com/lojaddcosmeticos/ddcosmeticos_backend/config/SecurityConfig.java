package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.handler.SecurityFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
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
public class SecurityConfig {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private SecurityFilter securityFilter; // <--- Injetamos o filtro aqui

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ====================================================
                        // 1. REGRAS PRIORITÁRIAS (Coloque o que falhou aqui em cima)
                        // ====================================================

                        // Força a liberação da rota de histórico com ID variável (*)
                        .requestMatchers("/api/v1/produtos/*/historico").permitAll()

                        // Libera todas as outras rotas de produtos (Lixeira, Restaurar, etc)
                        .requestMatchers("/api/v1/produtos/**").permitAll()
                        .requestMatchers("/produtos/**").permitAll()

                        // ====================================================
                        // 2. OUTRAS ROTAS PÚBLICAS
                        // ====================================================
                        .requestMatchers("/api/fiscal/**").permitAll()
                        .requestMatchers("/api/estoque/**").permitAll()
                        .requestMatchers("/api/precificacao/**").permitAll()
                        .requestMatchers("/api/catalogo/**").permitAll()
                        .requestMatchers("/api/auditoria/**").permitAll()
                        .requestMatchers("/api/operacoes/**").permitAll()
                                // Dentro do seu SecurityConfig.java
                                .requestMatchers(HttpMethod.GET, "/api/v1/relatorios/**").hasAnyRole("ADMIN", "USUARIO")
// OU se quiser deixar aberto para teste rápido:
                                .requestMatchers("/api/v1/relatorios/**").permitAll()

                        .requestMatchers("/dashboard/**", "/api/v1/dashboard/**").permitAll()
                        .requestMatchers("/auth/**", "/api/v1/auth/**").permitAll()

                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/imagens/**").permitAll()

                        // ====================================================
                        // 3. BLOQUEIO GERAL
                        // ====================================================
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Adicione este Bean para configurar as origens permitidas
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Use "*" apenas em desenvolvimento. Em prod, coloque a URL exata.
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:5173", "*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(false); // Se usar "*" em origins, credentials DEVE ser false

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}