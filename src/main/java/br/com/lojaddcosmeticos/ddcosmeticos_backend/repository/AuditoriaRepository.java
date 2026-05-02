package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    // 🚨 OTIMIZADO (CRÍTICO): Substituído findAll() por busca com limite de datas.
    // O Relatório PDF deve buscar apenas as ações de um determinado período (ex: última semana ou mês).
    List<Auditoria> findByDataHoraBetweenOrderByDataHoraDesc(LocalDateTime inicio, LocalDateTime fim);

    // ✅ MANTIDO E VALIDADO: A busca com filtros no frontend já está perfeitamente paginada.
    @Query("SELECT a FROM Auditoria a WHERE " +
            "(LOWER(a.mensagem) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(a.usuarioResponsavel) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            " LOWER(a.tipoEvento) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND a.dataHora BETWEEN :inicio AND :fim")
    Page<Auditoria> buscarPorFiltros(
            @Param("search") String search,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            Pageable pageable
    );
}