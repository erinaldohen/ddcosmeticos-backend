package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
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
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true) // Ativa @PreAuthorize nos controllers
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
                        // 1. ROTAS PÚBLICAS (Login, Swagger, Docs)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**", "/auth/**").permitAll()
                        .requestMatchers("/h2-console/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // =====================================================================
                        // 2. REGRAS DE LEITURA (GET) - TÊM QUE VIR PRIMEIRO!
                        // =====================================================================
                        // Libera listagem de produtos para PDV e Operadores
                        .requestMatchers(HttpMethod.GET, "/api/v1/produtos/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/produtos").authenticated()

                        // Libera status do caixa e consultas de venda
                        .requestMatchers(HttpMethod.GET, "/api/v1/caixa/status").authenticated()

                        // =====================================================================
                        // 3. REGRAS OPERACIONAIS (POST/PUT que não são de Admin)
                        // =====================================================================
                        .requestMatchers(HttpMethod.POST, "/api/v1/vendas/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/caixa/abrir").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/caixa/fechar").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/caixa/movimentacao").authenticated()

                        // =====================================================================
                        // 4. REGRAS ADMINISTRATIVAS (Restringe todo o resto a ADMIN)
                        // =====================================================================

                        // Qualquer escrita em produtos (Criar, Editar, Deletar)
                        .requestMatchers(HttpMethod.POST, "/api/v1/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/produtos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/produtos/**").hasRole("ADMIN")

                        // Áreas exclusivas
                        .requestMatchers("/api/v1/dashboard/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/fiscal/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/auditoria/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/caixa/historico").hasRole("ADMIN")

                        // 5. BLOQUEIO PADRÃO (O que não foi listado acima, exige login)
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
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}