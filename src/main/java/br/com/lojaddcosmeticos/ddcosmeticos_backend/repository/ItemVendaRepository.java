package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemVendaRepository extends JpaRepository<ItemVenda, Long> {

    /**
     * Agrupa as vendas por produto e ordena pelo valor total vendido (Decrescente).
     * O 'new ...ItemAbcDTO' cria o objeto automaticamente a partir do resultado do banco.
     */
    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO(" +
            "  p.codigoBarras, " +
            "  p.descricao, " +
            "  SUM(i.quantidade), " +
            "  SUM(i.valorTotalItem) " +
            ") " +
            "FROM ItemVenda i JOIN i.produto p " +
            "GROUP BY p.codigoBarras, p.descricao " +
            "ORDER BY SUM(i.valorTotalItem) DESC")
    List<ItemAbcDTO> agruparVendasPorProduto();
}