package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // --- MÉTODOS PARA O DIA A DIA (VENDA/PDV) ---
    // Respeitam o @SQLRestriction("ativo = true")
    // Só trazem produtos que podem ser vendidos.

    Optional<Produto> findByCodigoBarras(String codigoBarras);

    List<Produto> findByNomeContainingIgnoreCase(String nome);


    // --- MÉTODOS PARA O INVENTÁRIO E VALIDAÇÃO (ADMIN) ---
    // Ignoram o Soft Delete. Veem TUDO o que está no banco.

    // 1. Busca por EAN Irrestrita (Para checar se já existe antes de cadastrar)
    @Query(value = "SELECT * FROM produto WHERE codigo_barras = :codigoBarras", nativeQuery = true)
    Optional<Produto> findByCodigoBarrasIrrestrito(String codigoBarras);

    // 2. Busca por ID Irrestrita (Para ativar/reativar um produto específico)
    @Query(value = "SELECT * FROM produto WHERE id = :id", nativeQuery = true)
    Optional<Produto> findByIdIrrestrito(Long id);
}