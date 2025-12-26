package br.com.lojaddcosmeticos.ddcosmeticos_backend.infrastructure;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", ex.getMessage(), "timestamp", LocalDateTime.now()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<?> handleValidation(ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", ex.getMessage(), "timestamp", LocalDateTime.now()));
    }

    // --- NOVO: Handler Genérico de Segurança ---
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGlobalException(Exception ex) {
        log.error("Erro interno não tratado: ", ex); // Loga o erro real no servidor
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "erro", "Ocorreu um erro interno no servidor. Contate o suporte.",
                        "timestamp", LocalDateTime.now()
                ));
    }
}