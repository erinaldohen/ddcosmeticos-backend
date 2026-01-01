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
            var email = jwtService.validateToken(token);
            // Verifica se o token é válido (retornou matrícula)
            if (email != null && !email.isEmpty()) {
                UserDetails user = usuarioRepository.findByEmail(email).orElse(null);

                if (user != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        // --- CORREÇÃO CRÍTICA AQUI ---
        // Sem essa linha, a requisição para no filtro e não chega no H2/Swagger/API
        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;
        return authHeader.replace("Bearer ", "");
    }
}