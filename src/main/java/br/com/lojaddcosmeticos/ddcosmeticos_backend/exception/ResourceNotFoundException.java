// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/exception/ResourceNotFoundException.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.exception;

/**
 * Exceção customizada para quando um recurso (Produto, Usuário, Fornecedor)
 * não é encontrado no banco de dados. Mapeia para 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ResourceNotFoundException(String message) {
        super(message);
    }
}