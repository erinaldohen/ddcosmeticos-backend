package br.com.lojaddcosmeticos.ddcosmeticos_backend.handler;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.RateLimitException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // <--- GARANTE QUE ESTE HANDLER TENHA PRIORIDADE TOTAL
public class ApiExceptionHandler {

    // Método auxiliar para padronizar a resposta
    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String mensagem, Map<String, String> detalhes) {
        ErrorResponse error = new ErrorResponse(
                mensagem,
                status.value(),
                LocalDateTime.now(),
                detalhes
        );
        return ResponseEntity.status(status).body(error);
    }

    // ==================================================================================
    //  AUTENTICAÇÃO E LOGIN (Mapeamento exato para o Switch do Login.jsx)
    // ==================================================================================

    /**
     * CENÁRIO 1: LOGIN OU SENHA ERRADOS (Mas usuário existe)
     * Status: 401 Unauthorized
     * Frontend exibe: "Credenciais inválidas..."
     */
    @ExceptionHandler({BadCredentialsException.class, InternalAuthenticationServiceException.class})
    public ResponseEntity<ErrorResponse> handleBadCredentials(Exception ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Credenciais inválidas. Verifique seu login e senha.", null);
    }

    /**
     * CENÁRIO 2: USUÁRIO NÃO ENCONTRADO
     * Status: 404 Not Found
     * Frontend exibe: "Usuário não encontrado no sistema."
     * OBS: Requer authProvider.setHideUserNotFoundExceptions(false) no SecurityConfig.
     */
    @ExceptionHandler({UsernameNotFoundException.class, ResourceNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleUserNotFound(RuntimeException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Usuário ou recurso não encontrado no sistema.", null);
    }

    /**
     * CENÁRIO 3: CONTA BLOQUEADA / INATIVA
     * Status: 403 Forbidden
     * Frontend exibe: "Acesso negado. Sua conta pode estar inativa..."
     */
    @ExceptionHandler({DisabledException.class, LockedException.class, AccountExpiredException.class})
    public ResponseEntity<ErrorResponse> handleAccountStatus(RuntimeException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Acesso negado. Sua conta está inativa ou bloqueada.", null);
    }

    /**
     * CENÁRIO 4: ACESSO NEGADO (SEM PERMISSÃO EM ROTA)
     * Status: 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Acesso negado: Permissão insuficiente.", null);
    }

    /**
     * CENÁRIO 5: MUITAS TENTATIVAS (RATE LIMIT)
     * Status: 429 Too Many Requests
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas consecutivas. Aguarde.", null);
    }

    /**
     * Outros erros de autenticação genéricos
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        // Evita loop se nenhuma das anteriores capturar
        return buildResponse(HttpStatus.UNAUTHORIZED, "Falha na autenticação.", null);
    }

    // ==================================================================================
    //  VALIDAÇÕES DE NEGÓCIO E DADOS
    // ==================================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return buildResponse(HttpStatus.BAD_REQUEST, "Dados inválidos. Verifique os campos.", errors);
    }

    @ExceptionHandler({ValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBusinessException(RuntimeException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(DataIntegrityViolationException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Erro de integridade. Registro duplicado ou violação de chave.", null);
    }

    // ==================================================================================
    //  FALLBACK (ERRO 500)
    // ==================================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ex.printStackTrace(); // Loga o erro real no console do servidor
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor. Nossa equipe já foi notificada.", null);
    }

    public record ErrorResponse(
            String mensagem,
            int status,
            LocalDateTime timestamp,
            Map<String, String> detalhes
    ) {}
}