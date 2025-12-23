package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
// Se tiver o filtro, descomente:
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// import br.com.lojaddcosmeticos.ddcosmeticos_backend.infra.security.SecurityFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // @Autowired
    // private SecurityFilter securityFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .csrf(csrf -> csrf.disable()) // Desativa CSRF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 1. Libera o Login (com o prefixo /api/v1 correto)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register").permitAll()

                        // 2. Libera o H2 Console (Acesso à URL)
                        .requestMatchers("/h2-console/**").permitAll()

                        // 3. Libera o Swagger
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // Bloqueia o resto
                        .anyRequest().authenticated()
                )
                // 4. Libera o uso de Frames para o H2 funcionar (IMPORTANTE!)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))

                // Se tiver filtro, descomente:
                // .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
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