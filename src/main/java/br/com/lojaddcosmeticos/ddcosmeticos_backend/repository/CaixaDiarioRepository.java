package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaixaDiarioRepository extends JpaRepository<CaixaDiario, Long> {

    // Usado para verificar se um usuário específico tem caixa aberto
    Optional<CaixaDiario> findFirstByUsuarioAberturaAndStatus(Usuario usuario, StatusCaixa status);

    // --- MÉTODOS CRITICOS PARA CONTAS A PAGAR/RECEBER (Adicionados) ---

    // Busca o caixa aberto da loja (independente de usuário) para lançar vendas/pagamentos
    Optional<CaixaDiario> findByStatus(StatusCaixa status);

    // Busca o último caixa criado (útil para validações de sequência)
    Optional<CaixaDiario> findTopByOrderByIdDesc();

    // --- RELATÓRIOS ---

    // Método para paginação (usado na listagem da tela de Histórico)
    Page<CaixaDiario> findByDataAberturaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    // Método para PDF (Lista todos no período)
    List<CaixaDiario> findByDataAberturaBetweenOrderByDataAberturaDesc(LocalDateTime inicio, LocalDateTime fim);

    @Query("SELECT SUM(c.valorFechamento - c.valorCalculadoSistema) FROM CaixaDiario c " +
            "WHERE c.dataAbertura BETWEEN :inicio AND :fim")
    BigDecimal somarQuebrasDeCaixa(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}