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

    // 1. Busca TUDO ordenado por data de captura (Usado quando a chave do toggle está ATIVA)
    Page<NotaPendenteImportacao> findAllByOrderByDataCapturaDesc(Pageable pageable);

    // 2. Busca APENAS as que NÃO ESTÃO com um status específico (Usado quando a chave do toggle está INATIVA)
    // Nota: O status passado no Controller será "IMPORTADA"
    Page<NotaPendenteImportacao> findAllByStatusNotOrderByDataCapturaDesc(String status, Pageable pageable);

    // 3. Busca TUDO com filtro de datas (Emissão)
    @Query("SELECT n FROM NotaPendenteImportacao n WHERE " +
            "(SUBSTRING(CAST(n.dataEmissao AS string), 1, 10) BETWEEN :dataInicio AND :dataFim " +
            "OR (n.dataEmissao IS NULL AND SUBSTRING(CAST(n.dataCaptura AS string), 1, 10) BETWEEN :dataInicio AND :dataFim)) " +
            "ORDER BY n.dataCaptura DESC")
    Page<NotaPendenteImportacao> findByDataEmissaoBetweenOrderByDataCapturaDesc(
            @Param("dataInicio") String dataInicio,
            @Param("dataFim") String dataFim,
            Pageable pageable);

    // 4. Busca APENAS as PENDENTES com filtro de datas (Emissão)
    @Query("SELECT n FROM NotaPendenteImportacao n WHERE n.status <> :status AND " +
            "(SUBSTRING(CAST(n.dataEmissao AS string), 1, 10) BETWEEN :dataInicio AND :dataFim " +
            "OR (n.dataEmissao IS NULL AND SUBSTRING(CAST(n.dataCaptura AS string), 1, 10) BETWEEN :dataInicio AND :dataFim)) " +
            "ORDER BY n.dataCaptura DESC")
    Page<NotaPendenteImportacao> findByDataEmissaoBetweenAndStatusNotOrderByDataCapturaDesc(
            @Param("dataInicio") String dataInicio,
            @Param("dataFim") String dataFim,
            @Param("status") String status,
            Pageable pageable);

    // O método antigo buscarPendentesOrdenadas() foi removido pois a lógica de
    // paginação e exclusão de status agora é gerenciada pelos métodos acima.
}