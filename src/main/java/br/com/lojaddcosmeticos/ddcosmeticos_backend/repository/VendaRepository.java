package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VendaRepository extends JpaRepository<Venda, Long> {

    /**
     * Busca todas as vendas realizadas dentro de um intervalo de datas.
     * Usado para relatórios diários, mensais, etc.
     */
    List<Venda> findByDataVendaBetween(LocalDateTime inicio, LocalDateTime fim);
}