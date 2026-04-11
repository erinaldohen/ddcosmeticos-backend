package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByDocumento(String documento);
    Optional<Cliente> findByTelefone(String telefone);

    Page<Cliente> findByTipoPessoa(String tipoPessoa, Pageable pageable);
    Page<Cliente> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
}