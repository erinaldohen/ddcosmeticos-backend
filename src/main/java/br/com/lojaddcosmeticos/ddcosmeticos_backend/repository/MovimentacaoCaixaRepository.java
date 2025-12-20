package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimentacaoCaixaRepository extends JpaRepository<MovimentacaoCaixa, Long> {

    /**
     * Procura todas as movimentações manuais (Sangria/Suprimento) num intervalo de tempo.
     * Essencial para o cálculo de fecho de caixa diário.
     */
    List<MovimentacaoCaixa> findByDataHoraBetween(LocalDateTime inicio, LocalDateTime fim);
}