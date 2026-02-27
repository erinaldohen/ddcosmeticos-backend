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
    private UserDetailsService userDetailsService;

    // --- CONFIGURAÇÃO DO PROVIDER DE AUTENTICAÇÃO ---
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());

        // CORREÇÃO DE SEGURANÇA APLICADA:
        // TRUE = Impede Enumeração de Utilizadores. Um atacante não consegue descobrir
        // se o e-mail existe no sistema porque lançará sempre um erro genérico de credenciais.
        authProvider.setHideUserNotFoundExceptions(true);

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
                        // CORREÇÃO APLICADA: Substituído hasRole por hasAuthority para evitar o prefixo duplo "ROLE_ROLE_ADMIN"
                        .requestMatchers("/api/v1/configuracoes/**").hasAuthority("ROLE_ADMIN")

                        // Produtos (Escrita)
                        .requestMatchers(HttpMethod.POST, "/api/v1/produtos/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/produtos/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/produtos/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/produtos/**").hasAuthority("ROLE_ADMIN")

                        // Áreas Sensíveis (Dashboard resolvido)
                        .requestMatchers("/api/v1/dashboard/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/v1/fiscal/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/v1/auditoria/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/v1/fornecedores/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/v1/usuarios/**").hasAuthority("ROLE_ADMIN")

                        // 4. RESTO (Bloqueio padrão)
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .authenticationProvider(authenticationProvider())
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

        // CORREÇÃO DE SEGURANÇA APLICADA:
        // Como 'allowCredentials' está ativo (necessário para cookies/sessões seguras),
        // o Spring Security não permite origens curinga (*). O porto exato do React foi configurado.
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}