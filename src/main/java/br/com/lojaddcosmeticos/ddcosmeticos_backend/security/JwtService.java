package br.com.lojaddcosmeticos.ddcosmeticos_backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
                    // Se o seu repositório busca por e-mail, mantenha isso.
                    // Se busca por username/matricula, troque para o campo correspondente!
                    .withSubject(usuario.getEmail())
                    .withExpiresAt(getExpirationInstant())
                    .withClaim("perfil", usuario.getPerfilDoUsuario().name())
                    .withClaim("nome", usuario.getNome())
                    .sign(algorithm);
        } catch (JWTCreationException exception){
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
            // Agora o Java vai gritar no console se o token estiver expirado ou inválido!
            System.out.println("⚠️ Token rejeitado pelo JWT! Motivo: " + exception.getMessage());
            return "";
        }
    }

    private Instant getExpirationInstant() {
        // CORREÇÃO CRÍTICA: Instant.now() já pega o tempo global correto, sem brigar com fuso horário!
        return Instant.now().plus(EXPIRATION_HOURS, ChronoUnit.HOURS);
    }
}