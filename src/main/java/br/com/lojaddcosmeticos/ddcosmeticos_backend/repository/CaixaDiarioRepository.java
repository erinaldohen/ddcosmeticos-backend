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

    Optional<CaixaDiario> findFirstByUsuarioAberturaAndStatus(Usuario usuario, StatusCaixa status);

    Optional<CaixaDiario> findFirstByStatus(StatusCaixa status);

    Optional<CaixaDiario> findTopByOrderByIdDesc();

    // ==================================================================================
    // RELATÓRIOS E DASHBOARD MATEMÁTICO (Zero Impacto na Memória)
    // ==================================================================================

    @Query("SELECT SUM(c.saldoAtual) FROM CaixaDiario c WHERE c.status = 'ABERTO'")
    BigDecimal somarSaldosAtuais();

    @Query("SELECT SUM(c.valorFisicoInformado - c.saldoEsperadoSistema) " +
            "FROM CaixaDiario c " +
            "WHERE c.dataAbertura BETWEEN :inicio AND :fim " +
            "AND c.status = 'FECHADO'")
    BigDecimal somarQuebrasDeCaixa(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // ==================================================================================
    // LISTAGENS PAGINADAS
    // ==================================================================================

    Page<CaixaDiario> findByDataAberturaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    @Query(value = "SELECT c FROM CaixaDiario c LEFT JOIN FETCH c.usuarioAbertura",
            countQuery = "SELECT count(c) FROM CaixaDiario c")
    Page<CaixaDiario> findAllComUsuario(Pageable pageable);

    @Query(value = "SELECT c FROM CaixaDiario c LEFT JOIN FETCH c.usuarioAbertura WHERE c.dataAbertura BETWEEN :inicio AND :fim",
            countQuery = "SELECT count(c) FROM CaixaDiario c WHERE c.dataAbertura BETWEEN :inicio AND :fim")
    Page<CaixaDiario> findByDataAberturaBetweenComUsuario(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    // ==================================================================================
    // MÉTODOS CIRÚRGICOS (Para processamento em PDF/Relatórios internos)
    // AVISO DE ARQUITETURA: Não usar para montar as views do Dashboard
    // ==================================================================================

    List<CaixaDiario> findByDataAberturaBetweenOrderByDataAberturaDesc(LocalDateTime inicio, LocalDateTime fim);

    @Query("SELECT c FROM CaixaDiario c WHERE c.dataAbertura >= :inicio AND c.dataAbertura <= :fim")
    List<CaixaDiario> findCaixasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}