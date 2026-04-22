package br.com.lojaddcosmeticos.ddcosmeticos_backend.exception;

public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}