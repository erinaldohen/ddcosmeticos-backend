package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    // CORREÇÃO: Alterado de Optional<UserDetails> para Optional<Usuario>
    // O AuthorizationService aceita Usuario pois ele implementa UserDetails.
    // O CaixaController aceita Usuario pois é o que ele precisa.
    Optional<Usuario> findByMatriculaOrEmail(String matricula, String email);
}