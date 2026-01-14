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

        // Pula validação para rotas públicas (Login/Swagger) para não sujar o log
        String path = request.getRequestURI();
        if (path.contains("/auth") || path.contains("/swagger") || path.contains("/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = this.recoverToken(request);

        if (token != null) {
            try {
                // 1. Valida o token e recupera o "subject" (pode ser matricula ou email)
                var login = jwtService.validateToken(token);

                if (login != null && !login.isEmpty()) {
                    // 2. CORREÇÃO CRÍTICA: Busca por Matrícula OU E-mail
                    // Isso garante que o usuário seja encontrado independente de como o token foi gerado
                    UserDetails user = usuarioRepository.findByMatriculaOrEmail(login, login).orElse(null);

                    if (user != null) {
                        // 3. Sucesso: Autentica
                        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        // System.out.println("✅ Acesso liberado para: " + login);
                    } else {
                        System.out.println("❌ Token válido, mas usuário não encontrado no banco (Busca por Matrícula/Email): " + login);
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ Erro na validação do token: " + e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;
        // Limpeza robusta do token
        return authHeader.replace("Bearer ", "").replace("\"", "").replace("'", "").trim();
    }
}