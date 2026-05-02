package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimentacaoCaixaRepository extends JpaRepository<MovimentacaoCaixa, Long> {

    // ✅ PROTEGIDO: Para relatórios e extratos no Frontend (Evita carregar o ano inteiro)
    Page<MovimentacaoCaixa> findByDataHoraBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    // Método cirúrgico mantido (apenas para uso interno do serviço ao fechar UM caixa diário)
    List<MovimentacaoCaixa> findByDataHoraBetween(LocalDateTime inicio, LocalDateTime fim);

    // Busca ultra-rápida de motivos (Retorna apenas Strings, zero impacto de memória)
    @Query("SELECT DISTINCT m.motivo FROM MovimentacaoCaixa m WHERE m.motivo IS NOT NULL ORDER BY m.motivo ASC")
    List<String> findDistinctMotivos();
}