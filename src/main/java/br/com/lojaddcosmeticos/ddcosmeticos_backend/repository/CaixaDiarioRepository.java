package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaixaDiarioRepository extends JpaRepository<CaixaDiario, Long> {

    Optional<CaixaDiario> findFirstByUsuarioAberturaAndStatus(Usuario usuario, StatusCaixa status);

    // Método para paginação (usado na listagem da tela)
    Page<CaixaDiario> findByDataAberturaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    // --- ADICIONE ESTA LINHA (usada para o PDF) ---
    List<CaixaDiario> findByDataAberturaBetweenOrderByDataAberturaDesc(LocalDateTime inicio, LocalDateTime fim);
}