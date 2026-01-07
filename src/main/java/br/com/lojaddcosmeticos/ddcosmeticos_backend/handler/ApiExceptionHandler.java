package br.com.lojaddcosmeticos.ddcosmeticos_backend.handler;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Tratador global unificado de exceções.
 * Substitui o antigo GlobalExceptionHandler.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 1. Erros de Validação do DTO (@Valid, @NotNull, etc)
     * Retorna um Map com os campos inválidos para o Frontend marcar em vermelho.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = new ErrorResponse(
                "Dados inválidos. Verifique os campos.",
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 2. Recurso não encontrado (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * 3. Erros de Regra de Negócio (400)
     */
    @ExceptionHandler({ValidationException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBusinessException(RuntimeException ex) {
        ErrorResponse errorResponse = new ErrorResponse(
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value(),
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * 4. Erros de Integridade do Banco (Ex: EAN duplicado) (409)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(DataIntegrityViolationException ex) {
        // Tenta extrair uma mensagem mais amigável ou usa uma padrão
        String mensagem = "Erro de integridade de dados. Verifique se o registro (EAN/CNPJ) já existe.";

        ErrorResponse errorResponse = new ErrorResponse(
                mensagem,
                HttpStatus.CONFLICT.value(),
                LocalDateTime.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * 5. Fallback para erros gerais não tratados (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ex.printStackTrace(); // Loga no console do servidor para debug
        ErrorResponse errorResponse = new ErrorResponse(
                "Ocorreu um erro interno no servidor. Contate o suporte.",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                LocalDateTime.now(),
                null // Não enviar stacktrace para o cliente por segurança
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // DTO Interno para padronização da resposta JSON
    public record ErrorResponse(
            String mensagem,
            int status,
            LocalDateTime timestamp,
            Map<String, String> detalhes // Usado apenas para validação de campos
    ) {}
}