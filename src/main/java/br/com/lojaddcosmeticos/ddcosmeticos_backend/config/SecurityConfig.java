package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.handler.SecurityFilter;
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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final SecurityFilter securityFilter;

    // Injeção via Construtor (Melhor prática que @Autowired em campos)
    public SecurityConfig(UserDetailsService userDetailsService, SecurityFilter securityFilter) {
        this.userDetailsService = userDetailsService;
        this.securityFilter = securityFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Configuração explícita do CORS
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. REGRAS PÚBLICAS
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()

                        // Rotas específicas do Swagger/H2/Imagens
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/imagens/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                        // 2. REGRAS DE PRODUTOS E CATÁLOGO
                        // Nota: O Spring Security avalia na ordem. Se a primeira for permitAll, a segunda (admin) é ignorada para a mesma URL.
                        // Ajustei para usar hasAuthority para evitar o erro de prefixo "ROLE_"
                        .requestMatchers("/admin/**").hasAuthority(PerfilDoUsuario.ROLE_ADMIN.name())
                        .requestMatchers("/api/v1/catalogo/**").permitAll()
                        .requestMatchers("/api/v1/produtos/**").permitAll() // Se quiser restringir escrita, coloque antes do permitAll especificando o método (POST/PUT)

                        // 3. REGRAS DO PDV E VENDAS
                        .requestMatchers("/api/v1/vendas/**").authenticated()

                        // 4. REGRAS ESPECÍFICAS DE PERFIL (Correção: hasAuthority em vez de hasRole)
                        .requestMatchers("/api/v1/usuarios/**").hasAuthority(PerfilDoUsuario.ROLE_ADMIN.name())
                        .requestMatchers("/api/v1/relatorios/**").hasAnyAuthority(PerfilDoUsuario.ROLE_ADMIN.name(), PerfilDoUsuario.ROLE_USUARIO.name())

                        // 5. RESTO (Modo DEV liberado conforme solicitado)
                        .anyRequest().permitAll()
                )
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Permite o Frontend React (3000 e 5173) e qualquer outro (*) em dev
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:5173", "*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "x-auth-token")); // Adicionado headers comuns
        configuration.setAllowCredentials(false); // allowCredentials false é mais seguro quando se usa "*" no origin

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