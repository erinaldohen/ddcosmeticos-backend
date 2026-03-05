package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentacaoCaixaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CaixaService {

    private final CaixaDiarioRepository caixaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimentacaoCaixaRepository movimentacaoRepository;

    // ==================================================================================
    //  MÉTODOS DE INTEGRAÇÃO (USADOS POR OUTROS SERVICES)
    // ==================================================================================

    /**
     * Busca o caixa atualmente aberto.
     * Tenta primeiro o caixa do usuário logado, se não encontrar, tenta qualquer caixa aberto (fallback).
     */
    public CaixaDiario buscarCaixaAberto() {
        try {
            Usuario operador = getUsuarioLogado();
            Optional<CaixaDiario> caixaUser = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO);
            if (caixaUser.isPresent()) return caixaUser.get();
        } catch (Exception e) {
            // Ignora erro de usuário não logado (pode ser chamado por processo agendado)
        }
        return caixaRepository.findByStatus(StatusCaixa.ABERTO).orElse(null);
    }

    /**
     * Salva uma movimentação e atualiza o saldo do caixa em tempo real.
     */
    @Transactional
    public void salvarMovimentacao(MovimentacaoCaixa movimentacao) {
        if (movimentacao.getCaixa() == null) {
            CaixaDiario caixa = buscarCaixaAberto();
            if (caixa != null) {
                movimentacao.setCaixa(caixa);

                // As movimentações manuais sempre atualizam os totalizadores de Entrada/Saída
                BigDecimal valor = movimentacao.getValor();
                if (movimentacao.getTipo() == TipoMovimentacaoCaixa.ENTRADA || movimentacao.getTipo() == TipoMovimentacaoCaixa.SUPRIMENTO) {
                    caixa.setSaldoAtual(caixa.getSaldoAtual().add(valor));
                    caixa.setTotalEntradas(caixa.getTotalEntradas().add(valor));
                } else {
                    caixa.setSaldoAtual(caixa.getSaldoAtual().subtract(valor));
                    caixa.setTotalSaidas(caixa.getTotalSaidas().add(valor));
                }
                caixaRepository.save(caixa);
            }
        }
        movimentacaoRepository.save(movimentacao);
    }

    // ==================================================================================
    //  MÉTODOS OPERACIONAIS (FRONTEND)
    // ==================================================================================

    public List<String> listarMotivosFrequentes() {
        return movimentacaoRepository.findDistinctMotivos();
    }

    public List<MovimentacaoCaixa> buscarHistorico(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(23, 59, 59);
        return movimentacaoRepository.findByDataHoraBetween(dataInicio, dataFim);
    }

    // ----------------------------------------------------------------------------------
    // CORREÇÃO CRÍTICA AQUI: O CaixaService não deve recalcular usando Lista de Vendas.
    // Ele deve apenas devolver os acumuladores que o VendaService já preencheu lindamente.
    // ----------------------------------------------------------------------------------
    public CaixaDiarioDTO buscarStatusAtual() {
        Usuario operador = getUsuarioLogado();
        Optional<CaixaDiario> caixaOpt = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO);

        if (caixaOpt.isEmpty()) return null;

        CaixaDiario caixa = caixaOpt.get();

        // O saldo atual em gaveta = Saldo Inicial + Entradas(Suprimentos) + Vendas em Dinheiro - Saídas(Sangrias)
        // OBS: Pagamentos digitais não afetam a gaveta, apenas os relatórios fiscais.
        BigDecimal saldoGaveta = caixa.getSaldoInicial()
                .add(caixa.getTotalEntradas())
                .add(caixa.getTotalVendasDinheiro())
                .subtract(caixa.getTotalSaidas());

        return new CaixaDiarioDTO(
                caixa.getId(),
                "ABERTO",
                caixa.getSaldoInicial(),
                saldoGaveta, // Passamos o saldo físico calculado na hora
                caixa.getTotalEntradas(),
                caixa.getTotalSaidas(),
                caixa.getTotalVendasDinheiro(), // Lê direto do BD
                caixa.getTotalVendasPix(),      // Lê direto do BD
                caixa.getTotalVendasCredito(),  // Lê direto do BD
                caixa.getTotalVendasDebito()    // Lê direto do BD
        );
    }

    @Transactional
    public CaixaDiario abrirCaixa(BigDecimal fundoTroco) {
        Usuario operador = getUsuarioLogado();

        if (caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Você já possui um caixa aberto.");
        }

        CaixaDiario caixa = new CaixaDiario();
        caixa.setUsuarioAbertura(operador);
        caixa.setDataAbertura(LocalDateTime.now());
        caixa.setSaldoInicial(fundoTroco != null ? fundoTroco : BigDecimal.ZERO);
        caixa.setSaldoAtual(caixa.getSaldoInicial());
        caixa.setStatus(StatusCaixa.ABERTO);

        // Inicializa acumuladores
        caixa.setTotalVendasDinheiro(BigDecimal.ZERO);
        caixa.setTotalVendasPix(BigDecimal.ZERO);
        caixa.setTotalVendasCredito(BigDecimal.ZERO);
        caixa.setTotalVendasDebito(BigDecimal.ZERO);
        caixa.setTotalVendasCartao(BigDecimal.ZERO);
        caixa.setTotalEntradas(BigDecimal.ZERO);
        caixa.setTotalSaidas(BigDecimal.ZERO);

        return caixaRepository.save(caixa);
    }

    @Transactional
    public CaixaDiario fecharCaixa(BigDecimal saldoInformado) {
        if (saldoInformado == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo final (gaveta) obrigatório.");

        Usuario operador = getUsuarioLogado();
        CaixaDiario caixa = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não há caixa aberto para este usuário."));

        LocalDateTime agora = LocalDateTime.now();

        // 1. Fórmula do Saldo Esperado (Fundo + VendasDinheiro + Suprimentos - Sangrias)
        // Mais uma vez, não precisamos percorrer a lista de Venda.
        BigDecimal saldoEsperado = caixa.getSaldoInicial()
                .add(caixa.getTotalVendasDinheiro())
                .add(caixa.getTotalEntradas())
                .subtract(caixa.getTotalSaidas());

        // 2. Preenche dados finais
        caixa.setValorCalculadoSistema(saldoEsperado);
        caixa.setValorFechamento(saldoInformado);
        caixa.setSaldoAtual(saldoEsperado);
        caixa.setDataFechamento(agora);
        caixa.setStatus(StatusCaixa.FECHADO);

        return caixaRepository.save(caixa);
    }

    // --- SANGRIA E SUPRIMENTO ---

    @Transactional
    public void realizarSangria(BigDecimal valor, String observacao) {
        CaixaDiario caixa = buscarCaixaAberto();
        if (caixa == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caixa fechado ou não encontrado.");

        if (valor.compareTo(caixa.getSaldoAtual()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo insuficiente para sangria.");
        }

        MovimentacaoCaixa mov = new MovimentacaoCaixa();
        mov.setCaixa(caixa);
        mov.setTipo(TipoMovimentacaoCaixa.SANGRIA);
        mov.setValor(valor);
        mov.setMotivo("SANGRIA: " + observacao);
        mov.setDataHora(LocalDateTime.now());

        // Chama o método auxiliar que centraliza o recálculo do saldo
        salvarMovimentacao(mov);
    }

    @Transactional
    public void realizarSuprimento(BigDecimal valor, String observacao) {
        CaixaDiario caixa = buscarCaixaAberto();
        if (caixa == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Caixa fechado ou não encontrado.");

        MovimentacaoCaixa mov = new MovimentacaoCaixa();
        mov.setCaixa(caixa);
        mov.setTipo(TipoMovimentacaoCaixa.SUPRIMENTO);
        mov.setValor(valor);
        mov.setMotivo("SUPRIMENTO: " + observacao);
        mov.setDataHora(LocalDateTime.now());

        salvarMovimentacao(mov);
    }

    // --- UTILS ---

    private Usuario getUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        }
        String login = auth.getName();
        return usuarioRepository.findByMatriculaOrEmail(login, login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));
    }
}