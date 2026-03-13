package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.InteracaoCRM;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InteracaoCRMRepository extends JpaRepository<InteracaoCRM, Long> {

    // Lista todas as interações de um cliente específico
    List<InteracaoCRM> findByClienteOrderByDataContatoDesc(Cliente cliente);

    // Busca se existe algum contato RECENTE para não ficar spammando o cliente
    @Query("SELECT i FROM InteracaoCRM i WHERE i.cliente.id = :clienteId AND i.dataContato >= :dataLimite ORDER BY i.dataContato DESC LIMIT 1")
    Optional<InteracaoCRM> buscarContatoRecente(@Param("clienteId") Long clienteId, @Param("dataLimite") LocalDateTime dataLimite);
}