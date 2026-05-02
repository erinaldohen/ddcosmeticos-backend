package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DevedorResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusTitulo;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.TituloReceber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TituloReceberRepository extends JpaRepository<TituloReceber, Long> {

    // 1. ✅ OTIMIZADO: Retorna Page para carregar o Dashboard de Inadimplência infinitamente sem travar.
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
    Page<DevedorResumoDTO> findResumoDevedores(Pageable pageable);

    // 2. CIRÚRGICA: Busca boletos/faturas ABERTAS de um cliente (costumam ser poucos, List é seguro)
    List<TituloReceber> findByClienteIdAndStatusNotInOrderByDataVencimentoAsc(
            Long clienteId, List<StatusTitulo> statusIgnorados);

    // 3. ✅ OTIMIZADO: O Histórico COMPLETO de um cliente agora tem Paginação para proteger a RAM.
    Page<TituloReceber> findByClienteIdOrderByDataCompraDesc(Long clienteId, Pageable pageable);
}