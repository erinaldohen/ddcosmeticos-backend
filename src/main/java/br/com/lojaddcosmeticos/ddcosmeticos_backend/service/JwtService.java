// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/service/JwtService.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

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

/**
 * Serviço responsável por gerar, validar e manipular tokens JWT.
 */
@Service
public class JwtService {

    // Chave secreta para assinatura do token. DEVE ser segura e mantida em segredo.
    @Value("${api.security.token.secret:DDCOSMETICOS_SECRET_DEFAULT_TEST_KEY}")
    private String secret;

    // Configuração de tempo de expiração do token (Ex: 2 horas)
    private static final long EXPIRATION_HOURS = 2;

    /**
     * Gera um token JWT com base nas informações do usuário.
     */
    public String generateToken(Usuario usuario) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("ddcosmeticos-api") // Quem emitiu
                    .withSubject(usuario.getMatricula()) // Username principal
                    .withExpiresAt(getExpirationInstant()) // Tempo de expiração
                    .withClaim("perfil", usuario.getPerfil()) // Adiciona o perfil (role)
                    .withClaim("nome", usuario.getNome())
                    .sign(algorithm);
        } catch (JWTCreationException exception){
            throw new RuntimeException("Erro ao gerar token JWT", exception);
        }
    }

    /**
     * Valida um token e retorna a matrícula (subject) se for válido.
     */
    public String validateToken(String token){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("ddcosmeticos-api")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception){
            // Token inválido, expirado ou com problemas de assinatura
            return "";
        }
    }

    /**
     * Calcula o instante de expiração (2 horas a partir de agora).
     */
    private Instant getExpirationInstant() {
        return LocalDateTime.now().plusHours(EXPIRATION_HOURS).toInstant(ZoneOffset.of("-03:00"));
    }
}