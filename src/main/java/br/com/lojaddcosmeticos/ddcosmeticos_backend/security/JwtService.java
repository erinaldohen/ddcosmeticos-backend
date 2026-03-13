package br.com.lojaddcosmeticos.ddcosmeticos_backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
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
                    /* DICA DD COSMÉTICOS: Use a Matricula como Subject.
                       Isso garante que o SecurityFilter encontre o usuário
                       independente de ele ter logado com e-mail ou número de matrícula.
                    */
                    .withSubject(usuario.getMatricula())
                    .withExpiresAt(getExpirationInstant())
                    .withClaim("perfil", usuario.getPerfilDoUsuario().name())
                    .withClaim("nome", usuario.getNome())
                    .withClaim("email", usuario.getEmail()) // E-mail vira um campo extra (claim)
                    .sign(algorithm);
        } catch (JWTCreationException exception){
            log.error("Erro crítico na geração de Token para usuário {}: {}", usuario.getMatricula(), exception.getMessage());
            throw new RuntimeException("Erro de segurança: Não foi possível gerar o token JWT.", exception);
        }
    }

    public String validateToken(String token){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception){
            // Log amigável para debug, sem poluir o console de produção
            log.warn("Tentativa de acesso com Token inválido ou expirado: {}", exception.getMessage());
            return "";
        }
    }

    private Instant getExpirationInstant() {
        // Instant.now() é imune a variações de fuso horário do servidor (UTC por padrão)
        return Instant.now().plus(EXPIRATION_HOURS, ChronoUnit.HOURS);
    }
}