package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // ✅ CORRIGIDO: Retorna Optional. Evita NullPointerException se alguém tentar
    // entrar com email ou matrícula que não existe.
    @Query("SELECT u FROM Usuario u WHERE u.email = :login OR u.matricula = :login")
    Optional<Usuario> findByLogin(@Param("login") String login);

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByMatriculaOrEmail(String matricula, String email);
}