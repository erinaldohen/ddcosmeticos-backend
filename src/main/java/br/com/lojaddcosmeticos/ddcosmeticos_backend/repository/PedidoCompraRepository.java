package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PedidoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoCompraRepository extends JpaRepository<PedidoCompra, Long> {

    // Para listar pedidos por status (ex: saber o que está em cotação)
    List<PedidoCompra> findByStatus(PedidoCompra.StatusPedido status);

    // Para histórico de compras de um fornecedor específico
    List<PedidoCompra> findByFornecedorNomeContainingIgnoreCase(String nome);
}