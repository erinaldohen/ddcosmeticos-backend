package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfirmacaoFechamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentacaoCaixaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@Slf4j
public class CaixaService {

    private final CaixaDiarioRepository caixaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimentacaoCaixaRepository movimentacaoRepository;
    private final AuditoriaService auditoriaService;
    private final CaixaAuditorIaService auditorIaService;

    // ==================================================================================
    //  MÉTODOS DE INTEGRAÇÃO (USADOS POR OUTROS SERVICES)
    // ==================================================================================

    public CaixaDiario buscarCaixaAberto() {
        try {
            Usuario operador = getUsuarioLogado();
            Optional<CaixaDiario> caixaUser = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO);
            if (caixaUser.isPresent()) return caixaUser.get();
        } catch (Exception e) {
            // Ignora erro
        }
        return caixaRepository.findByStatus(StatusCaixa.ABERTO).orElse(null);
    }

    @Transactional
    public void salvarMovimentacao(MovimentacaoCaixa movimentacao) {
        if (movimentacao.getCaixa() == null) {
            CaixaDiario caixa = buscarCaixaAberto();
            if (caixa != null) {
                movimentacao.setCaixa(caixa);

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

    @Transactional(readOnly = true)
    public CaixaDiarioDTO buscarStatusAtual() {
        Usuario operador = getUsuarioLogado();
        Optional<CaixaDiario> caixaOpt = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO);

        if (caixaOpt.isEmpty()) return null;

        CaixaDiario caixa = caixaOpt.get();

        BigDecimal saldoGaveta = caixa.getSaldoInicial()
                .add(caixa.getTotalEntradas())
                .add(caixa.getTotalVendasDinheiro())
                .subtract(caixa.getTotalSaidas());

        String nomeOperador = caixa.getUsuarioAbertura() != null ? caixa.getUsuarioAbertura().getNome() : "Operador";

        return new CaixaDiarioDTO(
                caixa.getId(),
                "ABERTO",
                caixa.getDataAbertura(),
                null,
                nomeOperador,
                caixa.getSaldoInicial(),
                saldoGaveta,
                caixa.getTotalEntradas(),
                caixa.getTotalSaidas(),
                caixa.getTotalVendasDinheiro(),
                caixa.getTotalVendasPix(),
                caixa.getTotalVendasCredito(),
                caixa.getTotalVendasDebito(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null, // justificativaDiferenca (Ainda está aberto)
                null  // analiseAuditoriaIa (Ainda está aberto)
        );
    }

    @Transactional
    public CaixaDiarioDTO abrirCaixa(BigDecimal fundoTroco) {
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

        caixa.setTotalVendasDinheiro(BigDecimal.ZERO);
        caixa.setTotalVendasPix(BigDecimal.ZERO);
        caixa.setTotalVendasCredito(BigDecimal.ZERO);
        caixa.setTotalVendasDebito(BigDecimal.ZERO);
        caixa.setTotalVendasCartao(BigDecimal.ZERO);
        caixa.setTotalEntradas(BigDecimal.ZERO);
        caixa.setTotalSaidas(BigDecimal.ZERO);

        CaixaDiario caixaSalvo = caixaRepository.save(caixa);
        return converterParaDTOCompleto(caixaSalvo);
    }

    public CaixaDiarioDTO converterParaDTOCompleto(CaixaDiario caixa) {
        BigDecimal saldoGaveta = caixa.getSaldoAtual() != null ? caixa.getSaldoAtual() : BigDecimal.ZERO;

        String nomeOperador = "Operador Não Identificado";
        if (caixa.getUsuarioAbertura() != null) {
            nomeOperador = caixa.getUsuarioAbertura().getNome();
        }

        return new CaixaDiarioDTO(
                caixa.getId(),
                caixa.getStatus().name(),
                caixa.getDataAbertura(),
                caixa.getDataFechamento(),
                nomeOperador,
                caixa.getSaldoInicial(),
                saldoGaveta,
                caixa.getTotalEntradas(),
                caixa.getTotalSaidas(),
                caixa.getTotalVendasDinheiro(),
                caixa.getTotalVendasPix(),
                caixa.getTotalVendasCredito(),
                caixa.getTotalVendasDebito(),
                caixa.getSaldoEsperadoSistema(),
                caixa.getValorFisicoInformado(),
                caixa.getDiferencaCaixa(),
                caixa.getJustificativaDiferenca(), // AGORA SIM: Extrai do banco
                caixa.getAnaliseAuditoriaIa()      // AGORA SIM: Extrai do banco
        );
    }

    @Transactional
    public ConfirmacaoFechamentoDTO fecharCaixa(BigDecimal valorFisicoInformado, String justificativa) {
        Usuario operadorLogado = getUsuarioLogado();
        CaixaDiario caixa;

        Optional<CaixaDiario> caixaProprio = caixaRepository.findFirstByUsuarioAberturaAndStatus(operadorLogado, StatusCaixa.ABERTO);

        if (caixaProprio.isPresent()) {
            caixa = caixaProprio.get();
        } else if (operadorLogado.getPerfilDoUsuario() == PerfilDoUsuario.ROLE_ADMIN) {
            caixa = caixaRepository.findByStatus(StatusCaixa.ABERTO)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Não há nenhum caixa aberto no sistema."));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bloqueio de Segurança: Você não possui um caixa aberto no seu nome.");
        }

        BigDecimal saldoInicial = caixa.getSaldoInicial() != null ? caixa.getSaldoInicial() : BigDecimal.ZERO;
        BigDecimal suprimentos = caixa.getTotalEntradas() != null ? caixa.getTotalEntradas() : BigDecimal.ZERO;
        BigDecimal vendasDinheiro = caixa.getTotalVendasDinheiro() != null ? caixa.getTotalVendasDinheiro() : BigDecimal.ZERO;
        BigDecimal sangrias = caixa.getTotalSaidas() != null ? caixa.getTotalSaidas() : BigDecimal.ZERO;

        BigDecimal saldoEsperado = saldoInicial.add(suprimentos).add(vendasDinheiro).subtract(sangrias);
        BigDecimal valorInformado = valorFisicoInformado != null ? valorFisicoInformado : BigDecimal.ZERO;
        BigDecimal diferenca = valorInformado.subtract(saldoEsperado);

        if (diferenca.compareTo(BigDecimal.ZERO) != 0 && (justificativa == null || justificativa.trim().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Divergência detectada. É obrigatório fornecer uma justificativa para o administrador.");
        }

        caixa.setValorFisicoInformado(valorInformado);
        caixa.setSaldoEsperadoSistema(saldoEsperado);
        caixa.setDiferencaCaixa(diferenca);
        caixa.setJustificativaDiferenca(justificativa);
        caixa.setStatus(StatusCaixa.FECHADO);
        caixa.setDataFechamento(LocalDateTime.now());

        caixaRepository.save(caixa);

        String msgAuditoria = String.format("Fechamento Caixa #%d. Resp: %s. Esperado: %s. Informado: %s. Dif: %s",
                caixa.getId(), operadorLogado.getNome(), saldoEsperado, valorInformado, diferenca);

        if (diferenca.compareTo(BigDecimal.ZERO) != 0) {
            if (diferenca.compareTo(BigDecimal.ZERO) < 0) {
                auditoriaService.registrar("QUEBRA_DE_CAIXA", msgAuditoria);
                log.warn("QUEBRA DETECTADA: {}", msgAuditoria);
            } else {
                auditoriaService.registrar("SOBRA_DE_CAIXA", msgAuditoria);
                log.info("SOBRA DETECTADA: {}", msgAuditoria);
            }

            if (auditorIaService != null) {
                auditorIaService.auditarQuebraDeCaixa(caixa.getId(), operadorLogado.getNome(), justificativa);
            }

        } else {
            auditoriaService.registrar("FECHAMENTO_CAIXA", msgAuditoria);
        }

        return new ConfirmacaoFechamentoDTO(
                caixa.getId(),
                caixa.getUsuarioAbertura().getNome(),
                caixa.getDataFechamento(),
                saldoEsperado,
                valorInformado,
                diferenca
        );
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

    @Transactional(readOnly = true)
    public Page<CaixaDiarioDTO> listarHistoricoPaginado(LocalDate inicio, LocalDate fim, Pageable pageable) {
        Page<CaixaDiario> paginaCaixas;

        if (inicio != null && fim != null) {
            paginaCaixas = caixaRepository.findByDataAberturaBetweenComUsuario(
                    inicio.atStartOfDay(),
                    fim.atTime(23, 59, 59),
                    pageable
            );
        } else {
            paginaCaixas = caixaRepository.findAllComUsuario(pageable);
        }

        return paginaCaixas.map(this::converterParaDTOCompleto);
    }
    // ==================================================================================
    //  MÉTODOS DE AUDITORIA E ALERTAS
    // ==================================================================================
    @Transactional(readOnly = true)
    public List<CaixaDiarioDTO> buscarAlertasRiscoDashboard() {
        return caixaRepository.findAll().stream()
                .filter(c -> c.getAnaliseAuditoriaIa() != null &&
                        (c.getAnaliseAuditoriaIa().contains("[RISCO: ALTO]") || c.getAnaliseAuditoriaIa().contains("[RISCO: MEDIO]")))
                .map(this::converterParaDTOCompleto)
                .toList();
    }
}