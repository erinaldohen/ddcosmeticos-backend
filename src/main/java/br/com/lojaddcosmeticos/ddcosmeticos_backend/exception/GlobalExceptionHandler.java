package br.com.lojaddcosmeticos.ddcosmeticos_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final long serialVersionUID = 1L;
    // Trata erro de login (Senha inválida)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
        return montarErro(HttpStatus.FORBIDDEN, "Usuário ou senha inválidos.");
    }

    // Trata qualquer erro genérico não previsto
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneral(Exception ex) {
        // Em produção, logar o erro real no console para o desenvolvedor
        ex.printStackTrace();
        return montarErro(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno no servidor. Contate o suporte.");
    }

    // Método auxiliar para montar o JSON bonitinho
    private ResponseEntity<Object> montarErro(HttpStatus status, String mensagem) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("erro", status.getReasonPhrase());
        body.put("mensagem", mensagem);

        return ResponseEntity.status(status).body(body);
    }
}