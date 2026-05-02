package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.NotaPendenteImportacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotaPendenteImportacaoRepository extends JpaRepository<NotaPendenteImportacao, Long> {

    Optional<NotaPendenteImportacao> findByChaveAcesso(String chaveAcesso);

    boolean existsByChaveAcesso(String chaveAcesso);

    // --- MÉTODOS BASEADOS NO NOVO CONTROLLER (PAGINAÇÃO E TOGGLE SWITCH) ---

    // ✅ Validado: Perfeitamente paginado
    Page<NotaPendenteImportacao> findAllByOrderByDataCapturaDesc(Pageable pageable);

    Page<NotaPendenteImportacao> findAllByStatusNotOrderByDataCapturaDesc(String status, Pageable pageable);

    @Query("SELECT n FROM NotaPendenteImportacao n WHERE " +
            "(SUBSTRING(CAST(n.dataEmissao AS string), 1, 10) BETWEEN :dataInicio AND :dataFim " +
            "OR (n.dataEmissao IS NULL AND SUBSTRING(CAST(n.dataCaptura AS string), 1, 10) BETWEEN :dataInicio AND :dataFim)) " +
            "ORDER BY n.dataCaptura DESC")
    Page<NotaPendenteImportacao> findByDataEmissaoBetweenOrderByDataCapturaDesc(
            @Param("dataInicio") String dataInicio,
            @Param("dataFim") String dataFim,
            Pageable pageable);

    @Query("SELECT n FROM NotaPendenteImportacao n WHERE n.status <> :status AND " +
            "(SUBSTRING(CAST(n.dataEmissao AS string), 1, 10) BETWEEN :dataInicio AND :dataFim " +
            "OR (n.dataEmissao IS NULL AND SUBSTRING(CAST(n.dataCaptura AS string), 1, 10) BETWEEN :dataInicio AND :dataFim)) " +
            "ORDER BY n.dataCaptura DESC")
    Page<NotaPendenteImportacao> findByDataEmissaoBetweenAndStatusNotOrderByDataCapturaDesc(
            @Param("dataInicio") String dataInicio,
            @Param("dataFim") String dataFim,
            @Param("status") String status,
            Pageable pageable);
}