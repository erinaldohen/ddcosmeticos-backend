package br.com.lojaddcosmeticos.ddcosmeticos_backend.repository;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaixaDiarioRepository extends JpaRepository<CaixaDiario, Long> {

    // Usado para verificar se um usuário específico tem caixa aberto
    Optional<CaixaDiario> findFirstByUsuarioAberturaAndStatus(Usuario usuario, StatusCaixa status);

    // --- MÉTODOS CRITICOS PARA CONTAS A PAGAR/RECEBER ---

    // 🚨 ATUALIZADO: Usar findFirst evita crash (NonUniqueResultException) se houver 2 caixas abertos por anomalia
    Optional<CaixaDiario> findFirstByStatus(StatusCaixa status);

    // Busca o último caixa criado (útil para validações de sequência)
    Optional<CaixaDiario> findTopByOrderByIdDesc();

    // --- RELATÓRIOS E DASHBOARD ---

    // 🚨 ADICIONADO: Necessário para o Dashboard calcular o saldo em tempo real sem crashar
    @Query("SELECT SUM(c.saldoAtual) FROM CaixaDiario c WHERE c.status = 'ABERTO'")
    BigDecimal somarSaldosAtuais();

    // Método para paginação (usado na listagem da tela de Histórico)
    Page<CaixaDiario> findByDataAberturaBetween(LocalDateTime inicio, LocalDateTime fim, Pageable pageable);

    // Método para PDF (Lista todos no período)
    List<CaixaDiario> findByDataAberturaBetweenOrderByDataAberturaDesc(LocalDateTime inicio, LocalDateTime fim);

    @Query("SELECT SUM(c.valorFisicoInformado - c.saldoEsperadoSistema) " +
            "FROM CaixaDiario c " +
            "WHERE c.dataAbertura BETWEEN :inicio AND :fim " +
            "AND c.status = 'FECHADO'")
    BigDecimal somarQuebrasDeCaixa(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);

    // ==================================================================================
    //  MÉTODOS ADICIONADOS PARA RESOLVER O ERRO 500 (LAZY LOADING) NO HISTÓRICO WEB
    // ==================================================================================

    // Busca todos os caixas paginados, trazendo os dados do Usuário numa única ida ao banco
    @Query(value = "SELECT c FROM CaixaDiario c LEFT JOIN FETCH c.usuarioAbertura",
            countQuery = "SELECT count(c) FROM CaixaDiario c")
    Page<CaixaDiario> findAllComUsuario(Pageable pageable);

    // Busca os caixas paginados por data, trazendo os dados do Usuário numa única ida ao banco
    @Query(value = "SELECT c FROM CaixaDiario c LEFT JOIN FETCH c.usuarioAbertura WHERE c.dataAbertura BETWEEN :inicio AND :fim",
            countQuery = "SELECT count(c) FROM CaixaDiario c WHERE c.dataAbertura BETWEEN :inicio AND :fim")
    Page<CaixaDiario> findByDataAberturaBetweenComUsuario(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim, Pageable pageable);

    @Query("SELECT c FROM CaixaDiario c WHERE c.dataAbertura >= :inicio AND c.dataAbertura <= :fim")
    List<CaixaDiario> findCaixasNoPeriodo(@Param("inicio") LocalDateTime inicio, @Param("fim") LocalDateTime fim);
}