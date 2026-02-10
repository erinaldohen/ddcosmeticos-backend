package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // CORREÇÃO: Mapeia explicitamente 'login' para o campo 'email' do banco
    @Query("SELECT u FROM Usuario u WHERE u.email = :login")
    Usuario findByLogin(String login);

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByMatriculaOrEmail(String matricula, String email);
}