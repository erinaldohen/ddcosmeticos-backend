package br.com.lojaddcosmeticos.ddcosmeticos_backend.model;

public enum Perfil {
    ROLE_CAIXA,
    ROLE_GERENTE;

    // Método auxiliar para facilitar comparações se necessário
    public String getRole() {
        return this.name();
    }
}