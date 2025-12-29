package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    // ==================================================================================
    // SESSÃO 1: BUSCAS ESPECÍFICAS
    // ==================================================================================

    /**
     * Busca um cliente ativo pelo CPF.
     * Usado na validação da venda.
     */
    Optional<Cliente> findByCpf(String cpf);

    /**
     * Verifica se já existe cadastro para evitar duplicidade.
     */
    boolean existsByCpf(String cpf);
}