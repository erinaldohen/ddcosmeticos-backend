package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoEntradaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimentoEstoqueRepository extends JpaRepository<MovimentoEstoque, Long> {

    // ✅ BRILHANTE: Agregação nativa forte (Traz apenas o DTO Paginado)
    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoEntradaDTO(" +
            "m.documentoReferencia, " +
            "'1', " +
            "MAX(m.chaveAcesso), " +
            "MAX(m.dataMovimento), " +
            "f.nomeFantasia, " +
            "f.cnpj, " +
            "CAST(SUM(m.quantidadeMovimentada) AS Long), " +
            "SUM(m.quantidadeMovimentada * m.custoMovimentado)) " +
            "FROM MovimentoEstoque m JOIN m.fornecedor f " +
            "WHERE m.tipoMovimentoEstoque = 'ENTRADA' " +
            "GROUP BY m.documentoReferencia, f.nomeFantasia, f.cnpj " +
            "ORDER BY MAX(m.dataMovimento) DESC")
    Page<HistoricoEntradaDTO> buscarHistoricoEntradasAgrupado(Pageable pageable);

    // ✅ CIRÚRGICA: Traz os itens limitados a apenas UMA nota fiscal
    @Query("SELECT m FROM MovimentoEstoque m WHERE m.documentoReferencia = :numeroNota AND m.tipoMovimentoEstoque = 'ENTRADA'")
    List<MovimentoEstoque> buscarItensDaNota(@Param("numeroNota") String numeroNota);

    // Validações Boolean (Ultra leves no Banco)
    boolean existsByDocumentoReferenciaAndFornecedorAndTipoMovimentoEstoque(
            String documentoReferencia, Fornecedor fornecedor, TipoMovimentoEstoque tipoMovimentoEstoque);

    boolean existsByChaveAcesso(String chaveAcesso);
}