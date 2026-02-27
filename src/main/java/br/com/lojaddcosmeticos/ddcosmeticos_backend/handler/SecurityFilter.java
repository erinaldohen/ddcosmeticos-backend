package br.com.lojaddcosmeticos.ddcosmeticos_backend.handler;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.security.JwtService; // JwtService é o seu TokenService!
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    JwtService tokenService;

    @Autowired
    UsuarioRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);

        // LOGS DE RAIO-X PARA O CONSOLE DO BACKEND
        System.out.println("=== RAIO-X DE SEGURANÇA ===");
        System.out.println("Requisitando: " + request.getRequestURI());

        if (token != null) {
            var subject = tokenService.validateToken(token);
            System.out.println("Conteúdo lido de dentro do Token: " + subject);

            if (subject != null && !subject.isEmpty()) {
                // VERIFIQUE AQUI: Se o 'subject' for um e-mail, seu repositório precisa buscar por e-mail!
                UserDetails user = userRepository.findByLogin(subject);
                System.out.println("Usuário encontrado no Banco de Dados? " + (user != null ? "SIM" : "NÃO"));

                if (user != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    System.out.println("Permissões concedidas a este usuário: " + user.getAuthorities());
                }
            }
        } else {
            System.out.println("NENHUM TOKEN FOI RECEBIDO PELO BACKEND.");
        }
        System.out.println("===========================");

        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request){
        // 1. PRIMEIRO: Tenta pegar do Header 'Authorization' (Padrão do Axios/React)
        var authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.replace("Bearer ", "");
        }

        // 2. SEGUNDO: Tenta pegar dos Cookies (Caso use HttpOnly Cookies futuramente)
        if (request.getCookies() != null) {
            return Arrays.stream(request.getCookies())
                    .filter(c -> "jwt".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        return null;
    }
}