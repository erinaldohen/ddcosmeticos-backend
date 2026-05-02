package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.handler.SecurityFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    @Autowired private SecurityFilter securityFilter;
    @Autowired private UserDetailsService userDetailsService;

    // Injetamos o perfil ativo para saber se estamos em produção
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        authProvider.setHideUserNotFoundExceptions(true);
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isProd = "prod".equalsIgnoreCase(activeProfile);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable()) // Stateless JWT dispensa CSRF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"mensagem\": \"Acesso negado. Token inválido ou expirado.\", \"status\": 403}");
                        })
                )
                .authorizeHttpRequests(auth -> {
                    // ROTAS PÚBLICAS
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll();
                    auth.requestMatchers("/uploads/**").permitAll();

                    // 🚨 CORREÇÃO: Bloqueia H2 e Documentação em Produção
                    if (!isProd) {
                        auth.requestMatchers("/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }

                    // ROTAS DE ADMINISTRAÇÃO E GESTÃO
                    auth.requestMatchers("/api/v1/configuracoes/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(HttpMethod.POST, "/api/v1/produtos/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(HttpMethod.PUT, "/api/v1/produtos/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers(HttpMethod.DELETE, "/api/v1/produtos/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers("/api/v1/dashboard/**").hasAuthority("ROLE_ADMIN");
                    auth.requestMatchers("/api/v1/fiscal/**").hasAuthority("ROLE_ADMIN");

                    // PERMISSÃO PADRÃO PARA OPERADORES
                    auth.anyRequest().authenticated();
                })
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 🚨 CORREÇÃO: Remoção de Wildcards perigosos e definição estrita.
        // Em produção, você substituirá pelo domínio exato da loja.
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "https://ddcosmeticos-pdv.com.br" // Domínio de Prod
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}