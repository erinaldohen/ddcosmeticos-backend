// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/config/SecurityConfig.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configurações de segurança básicas: PasswordEncoder e UserDetailsService (busca de usuários).
 */
@Configuration
public class SecurityConfig {

    /**
     * Define o algoritmo de criptografia de senhas (BCrypt).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Define como o Spring Security deve buscar um usuário no banco (usando a matrícula como username).
     */
    @Bean
    public UserDetailsService userDetailsService(UsuarioRepository usuarioRepository) {
        return matricula -> usuarioRepository.findByMatricula(matricula)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado com matrícula: " + matricula));
    }
}