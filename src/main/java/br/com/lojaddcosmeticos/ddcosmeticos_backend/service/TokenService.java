package br.com.lojaddcosmeticos.ddcosmeticos_backend.service; // Ajuste o pacote se necessário

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
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    // GERA O TOKEN
    public String generateToken(Usuario usuario) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("auth-api")
                    .withSubject(usuario.getMatricula()) // IDENTIFICADOR: MATRÍCULA
                    .withClaim("role", usuario.getPerfilDoUsuario().name()) // Opcional: Ajuda no debug
                    .withExpiresAt(genExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    // VALIDA O TOKEN E RETORNA A MATRÍCULA
    public String validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("auth-api")
                    .build()
                    .verify(token) // Se expirou, lança exceção aqui
                    .getSubject();
        } catch (JWTVerificationException exception) {
            // Retorna vazio para que o Filter bloqueie o acesso
            return "";
        }
    }

    private Instant genExpirationDate() {
        // Expira em 2 horas (Brasília -03:00)
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }
}