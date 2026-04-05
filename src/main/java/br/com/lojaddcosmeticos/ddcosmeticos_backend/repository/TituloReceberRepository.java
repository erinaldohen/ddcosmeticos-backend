package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DevedorResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusTitulo;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.TituloReceber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TituloReceberRepository extends JpaRepository<TituloReceber, Long> {

    // 1. Agrupa os saldos devidos e atrasados por Cliente
    @Query("""
        SELECT new br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DevedorResumoDTO(
            c.id, c.nome, c.documento, c.telefone,
            SUM(t.saldoDevedor),
            SUM(CASE WHEN t.dataVencimento < CURRENT_DATE OR t.status = 'ATRASADO' THEN t.saldoDevedor ELSE 0 END)
        )
        FROM TituloReceber t
        JOIN t.cliente c
        WHERE t.status <> 'PAGO' AND t.status <> 'CANCELADO'
        GROUP BY c.id, c.nome, c.documento, c.telefone
        ORDER BY SUM(t.saldoDevedor) DESC
    """)
    List<DevedorResumoDTO> findResumoDevedores();

    // 2. Busca os boletos/faturas abertas de um cliente específico
    List<TituloReceber> findByClienteIdAndStatusNotInOrderByDataVencimentoAsc(
            Long clienteId, List<StatusTitulo> statusIgnorados);
    // Adicione junto das outras queries no seu TituloReceberRepository
    List<TituloReceber> findByClienteIdOrderByDataCompraDesc(Long clienteId);
}