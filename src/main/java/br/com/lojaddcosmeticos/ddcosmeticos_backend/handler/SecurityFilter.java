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

        String path = request.getRequestURI();
        // Ignora rotas públicas para limpar o log
        if (path.contains("/auth") || path.contains("/swagger") || path.contains("/h2-console")) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = this.recoverToken(request);

        if (token != null) {
            try {
                var login = jwtService.validateToken(token);

                // --- LOG DE DEBUG (Olhe no console do IntelliJ) ---
                System.out.println("🔍 TENTATIVA DE ACESSO: " + path);
                System.out.println("👤 Login extraído do Token: " + login);

                if (login != null && !login.isEmpty()) {
                    UserDetails user = usuarioRepository.findByMatriculaOrEmail(login, login).orElse(null);

                    if (user != null) {
                        System.out.println("✅ Usuário encontrado no Banco: " + user.getUsername());
                        System.out.println("🔐 Permissões (Roles): " + user.getAuthorities());

                        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        System.out.println("❌ Usuário NÃO encontrado no banco para o login: " + login);
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ Erro na validação do Token: " + e.getMessage());
            }
        } else {
            System.out.println("⚠️ Requisição sem Token: " + path);
        }

        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;
        return authHeader.replace("Bearer ", "").trim();
    }
}