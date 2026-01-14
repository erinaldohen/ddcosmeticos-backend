package br.com.lojaddcosmeticos.ddcosmeticos_backend.handler;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired private JwtService jwtService;
    @Autowired private UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var token = this.recoverToken(request);

        if (token != null) {
            try {
                // Valida o token e recupera o sujeito (Matrícula)
                // Se o token estiver expirado ou inválido, o JwtService deve retornar "" ou lançar exceção
                var matricula = jwtService.validateToken(token);

                if (matricula != null && !matricula.isEmpty()) {
                    // Busca usuário pela matrícula (Identificador principal do sistema)
                    UserDetails user = usuarioRepository.findByMatricula(matricula).orElse(null);

                    if (user != null) {
                        // Usuário encontrado e token válido: Autentica no contexto
                        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                // Em caso de erro na validação (token expirado/inválido), apenas limpamos o contexto
                // O Spring Security tratará isso retornando 403 Forbidden mais à frente
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Recupera e LIMPA o token do cabeçalho.
     * A correcao aqui remove aspas e espaços que o Frontend possa ter enviado errado.
     */
    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;

        // Remove "Bearer ", remove aspas duplas (se houver) e remove espaços em branco extras
        return authHeader.replace("Bearer ", "").replace("\"", "").trim();
    }
}