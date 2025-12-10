// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/exception/ValidationException.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.exception;

/**
 * Exceção customizada para regras de negócio não atendidas.
 * Mapeia para 400 Bad Request.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}