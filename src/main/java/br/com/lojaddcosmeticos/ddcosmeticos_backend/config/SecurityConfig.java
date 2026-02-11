package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.handler.SecurityFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private SecurityFilter securityFilter;

    @Autowired
    private UserDetailsService userDetailsService; // Necessário para configurar o Provider

    // --- CONFIGURAÇÃO DO PROVIDER DE AUTENTICAÇÃO ---
    // Essencial para permitir a distinção entre Erro 401 (Senha) e 404 (Usuário)
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        // FALSE = Lança UsernameNotFoundException (gerando erro 404 no Handler)
        // TRUE (Padrão) = Lança BadCredentialsException (gerando erro 401)
        authProvider.setHideUserNotFoundExceptions(false);

        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // --- Tratamento de Exceção para usuários não logados (Token inválido/ausente) ---
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"mensagem\": \"Acesso negado. Faça login para continuar.\", \"status\": 403}");
                        })
                )
                // -------------------------------------------------------------------------------

                .authorizeHttpRequests(auth -> auth
                        // 1. ROTAS PÚBLICAS
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**", "/auth/**").permitAll()
                        .requestMatchers("/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**", "/uploads/**").permitAll()

                        // 2. LEITURA E OPERACIONAL (Liberado para qualquer utilizador logado)
                        .requestMatchers(HttpMethod.GET, "/api/v1/produtos/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/caixa/status", "/api/v1/caixas/status").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auditoria/eventos").authenticated()

                        // Operações de Venda e Caixa
                        .requestMatchers(HttpMethod.POST, "/api/v1/vendas/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/caixa/**", "/api/v1/caixas/**").authenticated()

                        // 3. RESTRITO A ADMIN (Escrita e Gestão)
                        .requestMatchers("/api/v1/configuracoes/**").hasRole("ADMIN")

                        // Produtos (Escrita)
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
                .authenticationProvider(authenticationProvider()) // Registra o provider customizado
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

        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}