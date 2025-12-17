package br.com.lojaddcosmeticos.ddcosmeticos_backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class JwtService {

    @Value("${api.security.token.secret:DDCOSMETICOS_SECRET_DEFAULT_TEST_KEY}")
    private String secret;

    private static final long EXPIRATION_HOURS = 2;

    public String generateToken(Usuario usuario) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("ddcosmeticos-api")
                    .withSubject(usuario.getMatricula())
                    .withExpiresAt(getExpirationInstant())

                    // CORREÇÃO: Adicionado .name() para converter o Enum em String
                    .withClaim("perfil", usuario.getPerfil().name())

                    .withClaim("nome", usuario.getNome())
                    .sign(algorithm);
        } catch (JWTCreationException exception){
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    public String validateToken(String token){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("ddcosmeticos-api")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception){
            return "";
        }
    }

    private Instant getExpirationInstant() {
        return LocalDateTime.now().plusHours(EXPIRATION_HOURS).toInstant(ZoneOffset.of("-03:00"));
    }
}