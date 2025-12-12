package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);

    /**
     * Busca a Venda, os Itens e os Produtos dos itens em uma única query.
     * Resolve o erro de LazyInitializationException.
     */
    @Query("SELECT v FROM Venda v " +
            "JOIN FETCH v.itens i " +
            "JOIN FETCH i.produto p " +
            "WHERE v.id = :id")
    Optional<Venda> findByIdWithItens(@Param("id") Long id);
}