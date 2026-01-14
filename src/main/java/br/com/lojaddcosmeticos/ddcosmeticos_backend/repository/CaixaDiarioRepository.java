package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CaixaDiarioRepository extends JpaRepository<CaixaDiario, Long> {

    // MUDANÇA: 'findFirst' previne erro se houver duplicidade no banco
    Optional<CaixaDiario> findFirstByUsuarioAberturaAndStatus(Usuario usuario, StatusCaixa status);

    boolean existsByStatus(StatusCaixa status);
}