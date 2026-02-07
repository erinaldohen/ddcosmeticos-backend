package br.com.lojaddcosmeticos.ddcosmeticos_backend.exception;

import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Erros de Validação do Java (@Valid, @NotNull, etc)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest().body(errors);
    }

    // 2. Erros de Regra de Negócio (Estoque, Crédito, etc)
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> handleBusinessException(ValidationException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage()); // Retorna a mensagem "Estoque insuficiente..."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // 3. Erros de JSON/Enum (Ex: enviar texto em campo numérico ou Enum inválido)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleJsonErrors(HttpMessageNotReadableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "Erro no formato dos dados enviados (JSON/Enum inválido).");
        return ResponseEntity.badRequest().body(error);
    }

    // 4. Erros Gerais
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        ex.printStackTrace(); // Loga no console do backend
        Map<String, String> error = new HashMap<>();
        error.put("message", "Erro interno do servidor: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<StandardError> handleResourceNotFoundException(ResourceNotFoundException e, HttpServletRequest request) {
        String error = "Recurso não encontrado";
        HttpStatus status = HttpStatus.NOT_FOUND;
        StandardError err = new StandardError(Instant.now(), status.value(), error, e.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(err);
    }
}