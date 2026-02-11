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

    // Método existente que você já usava para o relatório PDF geral
    List<Auditoria> findAllByOrderByDataHoraDesc();

    // --- NOVO MÉTODO (O que falta para corrigir o erro) ---
    // Esta query faz:
    // 1. Busca case-insensitive (LOWER) em mensagem, usuário OU tipoEvento
    // 2. E (AND) garante que esteja dentro do período de datas
    @Query("SELECT a FROM Auditoria a WHERE " +
            "(LOWER(a.mensagem) LIKE %:search% OR " +
            " LOWER(a.usuarioResponsavel) LIKE %:search% OR " +
            " LOWER(a.tipoEvento) LIKE %:search%) " +
            "AND a.dataHora BETWEEN :inicio AND :fim")
    Page<Auditoria> buscarPorFiltros(
            @Param("search") String search,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            Pageable pageable
    );
}