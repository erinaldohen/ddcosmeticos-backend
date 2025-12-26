package br.com.lojaddcosmeticos.ddcosmeticos_backend.security;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class JwtService {

    // Injeta a secret do properties, mas define um valor padrão se falhar
    @Value("${api.security.token.secret:segredo_padrao_para_desenvolvimento_apenas_123456}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private Long expiration;

    public String generateToken(Usuario usuario) {
        try {
            // Garante que a secret não é nula/vazia antes de usar
            if (secret == null || secret.isBlank()) {
                secret = "segredo_fallback_para_evitar_erro_500";
            }

            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("ddcosmeticos-api")
                    // --- CORREÇÃO AQUI: Usa getMatricula() em vez de getLogin() ---
                    .withSubject(usuario.getMatricula())
                    .withExpiresAt(genExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    public String validateToken(String token) {
        try {
            if (secret == null || secret.isBlank()) secret = "segredo_fallback_para_evitar_erro_500";

            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("ddcosmeticos-api")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            return "";
        }
    }

    private Instant genExpirationDate() {
        // Converte ms para horas
        long hours = expiration / 3600000;
        return LocalDateTime.now().plusHours(hours > 0 ? hours : 2).toInstant(ZoneOffset.of("-03:00"));
    }
}