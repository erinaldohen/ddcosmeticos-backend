package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemVendaRepository extends JpaRepository<ItemVenda, Long> {

    @Query("SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO(" +
            "p.codigoBarras, " +
            "p.descricao, " + // CORREÇÃO: p.nome -> p.descricao
            "SUM(i.quantidade), " +
            "SUM(i.precoUnitario * i.quantidade)) " +
            "FROM ItemVenda i " +
            "JOIN i.produto p " +
            "GROUP BY p.codigoBarras, p.descricao " + // CORREÇÃO: p.nome -> p.descricao
            "ORDER BY SUM(i.precoUnitario * i.quantidade) DESC")
    List<ItemAbcDTO> agruparVendasPorProduto();
}