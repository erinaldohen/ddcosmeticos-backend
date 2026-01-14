package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Busca principal para o login
    Optional<Usuario> findByEmail(String email);

    // Mantemos este para o SecurityFilter ser robusto (pode buscar por id ou email)
    Optional<Usuario> findByMatriculaOrEmail(String matricula, String email);

    Optional<Usuario> findByMatricula(String matricula);
}