package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ItemVendaRepository extends JpaRepository<ItemVenda, Long> {

    // CORREÇÃO APLICADA:
    // 1. Removido 'i.precoTotal' (que não existe no banco).
    // 2. Substituído por '(i.precoUnitario * i.quantidade)'.
    // 3. Adicionado CAST para Long e BigDecimal para evitar erro de construtor no H2/MySQL.

    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DashboardDTO$TopProdutoDTO(
            i.produto.descricao, 
            CAST(SUM(i.quantidade) AS long), 
            CAST(SUM(i.precoUnitario * i.quantidade) AS BigDecimal)
        ) 
        FROM ItemVenda i 
        WHERE i.venda.dataVenda >= :dataInicio 
        GROUP BY i.produto.descricao 
        ORDER BY SUM(i.precoUnitario * i.quantidade) DESC
    """)
    List<DashboardDTO.TopProdutoDTO> findTopProdutos(@Param("dataInicio") LocalDateTime dataInicio, Pageable pageable);
}