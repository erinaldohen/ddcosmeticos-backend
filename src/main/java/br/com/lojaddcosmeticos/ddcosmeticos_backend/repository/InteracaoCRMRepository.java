package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.InteracaoCRM;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InteracaoCRMRepository extends JpaRepository<InteracaoCRM, Long> {

    // ✅ OTIMIZADO: Transformado para Page para proteger a RAM caso um cliente fiel
    // tenha milhares de interações registadas no histórico
    Page<InteracaoCRM> findByClienteOrderByDataContatoDesc(Cliente cliente, Pageable pageable);

    // ✅ VALIDADO: Busca apenas o último registro (limitado pela query ou JPA nativamente)
    @Query("SELECT i FROM InteracaoCRM i WHERE i.cliente.id = :clienteId AND i.dataContato >= :dataLimite ORDER BY i.dataContato DESC LIMIT 1")
    Optional<InteracaoCRM> buscarContatoRecente(@Param("clienteId") Long clienteId, @Param("dataLimite") LocalDateTime dataLimite);
}