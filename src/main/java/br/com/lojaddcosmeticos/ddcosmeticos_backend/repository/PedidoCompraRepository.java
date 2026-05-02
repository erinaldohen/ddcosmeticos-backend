package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusPedido;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PedidoCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PedidoCompraRepository extends JpaRepository<PedidoCompra, Long> {

    // ✅ PROTEGIDO: Paginação obrigatória para listas de status (ex: relatórios de concluídos)
    Page<PedidoCompra> findByStatus(StatusPedido status, Pageable pageable);

    // ✅ PROTEGIDO: Histórico de fornecedores filtrado e paginado
    Page<PedidoCompra> findByFornecedorNomeContainingIgnoreCase(String nome, Pageable pageable);
}