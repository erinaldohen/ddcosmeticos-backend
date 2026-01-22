package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoEntradaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque; // Importante
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

    // QUERY DE AUDITORIA E HISTÓRICO AGRUPADO
    // CORRIGIDO: m.tipo -> m.tipoMovimentoEstoque
    // CORRIGIDO: m.quantidade -> m.quantidadeMovimentada
    // CORRIGIDO: m.valorUnitario -> m.custoMovimentado
    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoEntradaDTO(" +
            "   m.documentoReferencia, " +
            "   MAX(m.dataMovimento), " +
            "   f.nomeFantasia, " +
            "   f.cnpj, " +
            "   COUNT(m), " +
            "   SUM(m.quantidadeMovimentada * m.custoMovimentado) " +
            ") " +
            "FROM MovimentoEstoque m " +
            "JOIN m.fornecedor f " +
            "WHERE m.tipoMovimentoEstoque = 'ENTRADA' " +
            "GROUP BY m.documentoReferencia, f.nomeFantasia, f.cnpj " +
            "ORDER BY MAX(m.dataMovimento) DESC")
    Page<HistoricoEntradaDTO> buscarHistoricoEntradasAgrupado(Pageable pageable);

    // Busca os detalhes (itens) de uma nota específica para auditoria
    // CORRIGIDO: m.tipo -> m.tipoMovimentoEstoque
    @Query("SELECT m FROM MovimentoEstoque m WHERE m.documentoReferencia = :numeroNota AND m.tipoMovimentoEstoque = 'ENTRADA'")
    List<MovimentoEstoque> buscarItensDaNota(@Param("numeroNota") String numeroNota);

    // Método auxiliar usado na importação de XML para verificar duplicidade
    boolean existsByDocumentoReferenciaAndFornecedorAndTipoMovimentoEstoque(
            String documentoReferencia, Fornecedor fornecedor, TipoMovimentoEstoque tipoMovimentoEstoque);
}