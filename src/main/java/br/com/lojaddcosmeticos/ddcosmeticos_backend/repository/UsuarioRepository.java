// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/repository/UsuarioRepository.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Método de busca para a simulação de login
    Optional<Usuario> findByMatricula(String matricula);
}