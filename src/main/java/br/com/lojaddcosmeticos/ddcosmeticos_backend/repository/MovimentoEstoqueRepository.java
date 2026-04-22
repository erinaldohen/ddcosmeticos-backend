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

    // 1. QUERY DE AUDITORIA E HISTÓRICO AGRUPADO
    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.HistoricoEntradaDTO(" +
            "m.documentoReferencia, " +
            "'1', " +
            "MAX(m.chaveAcesso), " +   // 🔥 CORREÇÃO: Substituímos o '' pela Chave de Acesso Real!
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

    // 2. BUSCA OS DETALHES (ITENS) DE UMA NOTA ESPECÍFICA
    @Query("SELECT m FROM MovimentoEstoque m WHERE m.documentoReferencia = :numeroNota AND m.tipoMovimentoEstoque = 'ENTRADA'")
    List<MovimentoEstoque> buscarItensDaNota(@Param("numeroNota") String numeroNota);

    // 3. VALIDAÇÃO DE DUPLICIDADE
    // (O Spring faz a query automaticamente por causa do nome do método. NÃO usar @Query aqui!)
    boolean existsByDocumentoReferenciaAndFornecedorAndTipoMovimentoEstoque(
            String documentoReferencia, Fornecedor fornecedor, TipoMovimentoEstoque tipoMovimentoEstoque);

    boolean existsByChaveAcesso(String chaveAcesso);
}