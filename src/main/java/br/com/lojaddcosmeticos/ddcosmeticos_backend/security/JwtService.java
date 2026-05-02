package br.com.lojaddcosmeticos.ddcosmeticos_backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class JwtService {

    @Value("${api.security.token.secret}")
    private String secret;

    private static final long EXPIRATION_HOURS = 2;
    private static final String ISSUER = "ddcosmeticos-api";

    public String generateToken(Usuario usuario) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(usuario.getMatricula())
                    // ✅ OTIMIZAÇÃO: Apenas a Role trafega (necessário para o Spring Security).
                    // Ocultamos Nomes e Emails do Payload público.
                    .withClaim("role", usuario.getPerfilDoUsuario().name())
                    .withExpiresAt(getExpirationInstant())
                    .sign(algorithm);
        } catch (JWTCreationException exception){
            log.error("Erro crítico na geração de Token para usuário {}: {}", usuario.getMatricula(), exception.getMessage());
            throw new RuntimeException("Erro de segurança: Não foi possível gerar o token JWT.", exception);
        }
    }

    // ✅ OTIMIZAÇÃO: Devolve o Token descodificado completo, e não apenas o Subject,
    // para que o Filtro possa extrair as permissões sem bater na base de dados.
    public DecodedJWT validateTokenAndGetClaims(String token){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);
        } catch (JWTVerificationException exception){
            return null; // Retorna null silenciosamente, forçando o Filter a negar o acesso (403).
        }
    }

    private Instant getExpirationInstant() {
        return Instant.now().plus(EXPIRATION_HOURS, ChronoUnit.HOURS);
    }
}