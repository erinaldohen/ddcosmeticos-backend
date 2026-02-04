package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimentacaoCaixaRepository extends JpaRepository<MovimentacaoCaixa, Long> {

    List<MovimentacaoCaixa> findByDataHoraBetween(LocalDateTime inicio, LocalDateTime fim);

    /**
     * Busca todos os motivos únicos para o autocomplete.
     * CORREÇÃO: Campo alterado de 'observacao' para 'motivo' para bater com a Entidade.
     */
    @Query("SELECT DISTINCT m.motivo FROM MovimentacaoCaixa m WHERE m.motivo IS NOT NULL ORDER BY m.motivo ASC")
    List<String> findDistinctMotivos();
}