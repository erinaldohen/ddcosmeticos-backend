package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional; // Já importado anteriormente

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // CORREÇÃO: Alterado de UserDetails para Optional<Usuario>
    Optional<Usuario> findByMatricula(String matricula);

    boolean existsByMatricula(String matricula);
}