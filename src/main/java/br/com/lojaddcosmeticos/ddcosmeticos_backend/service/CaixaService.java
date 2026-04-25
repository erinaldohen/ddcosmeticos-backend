package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfirmacaoFechamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
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

    private final CaixaDiarioRepository caixaDiarioRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimentacaoCaixaRepository movimentacaoCaixaRepository;
    private final AuditoriaService auditoriaService;
    private final CaixaAuditorIaService caixaAuditorIaService;
    private final ConfiguracaoLojaService configuracaoLojaService;

    // ==================================================================================
    //  BLINDAGEM E RESUMO DO FECHAMENTO
    // ==================================================================================

    @Transactional(readOnly = true)
    public FechamentoCaixaDTO obterResumoFechamento(Long caixaId) {
        CaixaDiario caixa = caixaDiarioRepository.findById(caixaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caixa não encontrado."));

        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();

        // 1. Calcula o que deveria ter de dinheiro na gaveta
        BigDecimal saldoGavetaEsperado = nvl(caixa.getSaldoInicial())
                .add(nvl(caixa.getTotalEntradas()))
                .add(nvl(caixa.getTotalVendasDinheiro()))
                .subtract(nvl(caixa.getTotalSaidas()));

        BigDecimal brutoTotal = nvl(caixa.getTotalVendasDinheiro())
                .add(nvl(caixa.getTotalVendasPix()))
                .add(nvl(caixa.getTotalVendasCredito()))
                .add(nvl(caixa.getTotalVendasDebito()));

        // 2. Constrói o DTO Padrão (Sem a limitação de Setter)
        FechamentoCaixaDTO dtoBase = FechamentoCaixaDTO.builder()
                .caixaId(caixa.getId())
                .operador(caixa.getUsuarioAbertura() != null ? caixa.getUsuarioAbertura().getNome() : "Operador")
                .dataAbertura(caixa.getDataAbertura())
                .dataFechamento(caixa.getDataFechamento())
                .saldoInicial(nvl(caixa.getSaldoInicial()))
                .totalSuprimentos(nvl(caixa.getTotalEntradas()))
                .totalSangrias(nvl(caixa.getTotalSaidas()))
                .totalVendasDinheiro(nvl(caixa.getTotalVendasDinheiro()))
                .totalVendasPix(nvl(caixa.getTotalVendasPix()))
                .totalVendasCredito(nvl(caixa.getTotalVendasCredito()))
                .totalVendasDebito(nvl(caixa.getTotalVendasDebito()))
                .totalVendasCrediario(BigDecimal.ZERO) // Ajuste se armazenar Fiado no caixa futuramente
                .quantidadeVendas(0L) // Preencha se tiver a query de count
                .totalVendasBruto(brutoTotal)
                .saldoEsperadoDinheiroGaveta(saldoGavetaEsperado)
                .fechamentoCegoAtivo(false)
                .mensagemSistema("Resumo gerado com sucesso.")
                .build();

        // 🔥 3. A MÁGICA DA BLINDAGEM DO FECHAMENTO CEGO 🔥
        if (config != null && config.getFinanceiro() != null && Boolean.TRUE.equals(config.getFinanceiro().getFechamentoCego())) {
            // Usa o toBuilder() para clonar o objeto, apagar o valor sensível e reescrever a mensagem
            return dtoBase.toBuilder()
                    .saldoEsperadoDinheiroGaveta(null) // Esconde do Frontend!
                    .fechamentoCegoAtivo(true)
                    .mensagemSistema("FECHAMENTO CEGO ATIVO: Conte o dinheiro e as vias de cartão fisicamente e declare os valores abaixo.")
                    .build();
        }

        return dtoBase;
    }

    // Função utilitária para evitar NullPointerException em matemática
    private BigDecimal nvl(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }


    // ==================================================================================
    //  MÉTODOS DE INTEGRAÇÃO (USADOS POR OUTROS SERVICES)
    // ==================================================================================

    public CaixaDiario buscarCaixaAberto() {
        try {
            Usuario operador = getUsuarioLogado();
            Optional<CaixaDiario> caixaUser = caixaDiarioRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO);
            if (caixaUser.isPresent()) return caixaUser.get();
        } catch (Exception e) {
            // Ignora erro
        }
        return caixaDiarioRepository.findFirstByStatus(StatusCaixa.ABERTO).orElse(null);
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
                caixaDiarioRepository.save(caixa);
            }
        }
        movimentacaoCaixaRepository.save(movimentacao);
    }

    // ==================================================================================
    //  MÉTODOS OPERACIONAIS (FRONTEND)
    // ==================================================================================

    public List<String> listarMotivosFrequentes() {
        return movimentacaoCaixaRepository.findDistinctMotivos();
    }

    public List<MovimentacaoCaixa> buscarHistorico(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(23, 59, 59);
        return movimentacaoCaixaRepository.findByDataHoraBetween(dataInicio, dataFim);
    }

    @Transactional(readOnly = true)
    public CaixaDiarioDTO buscarStatusAtual() {
        Usuario operador = getUsuarioLogado();
        Optional<CaixaDiario> caixaOpt = caixaDiarioRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO);

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
                null,
                null
        );
    }

    @Transactional
    public CaixaDiarioDTO abrirCaixa(BigDecimal fundoTroco) {
        Usuario operador = getUsuarioLogado();

        if (caixaDiarioRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO).isPresent()) {
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

        CaixaDiario caixaSalvo = caixaDiarioRepository.save(caixa);
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
                caixa.getJustificativaDiferenca(),
                caixa.getAnaliseAuditoriaIa()
        );
    }

    @Transactional
    public ConfirmacaoFechamentoDTO fecharCaixa(BigDecimal valorFisicoInformado, String justificativa) {
        Usuario operadorLogado = getUsuarioLogado();
        CaixaDiario caixa;

        Optional<CaixaDiario> caixaProprio = caixaDiarioRepository.findFirstByUsuarioAberturaAndStatus(operadorLogado, StatusCaixa.ABERTO);

        if (caixaProprio.isPresent()) {
            caixa = caixaProprio.get();
        } else if (operadorLogado.getPerfilDoUsuario() == PerfilDoUsuario.ROLE_ADMIN) {
            caixa = caixaDiarioRepository.findFirstByStatus(StatusCaixa.ABERTO)
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

        caixaDiarioRepository.save(caixa);

        String msgAuditoria = String.format("Fechamento Caixa #%d. Resp: %s. Esperado: %s. Informado: %s. Dif: %s",
                caixa.getId(), operadorLogado.getNome(), saldoEsperado, valorInformado, diferenca);

        if (diferenca.compareTo(BigDecimal.ZERO) != 0) {
            if (diferenca.compareTo(BigDecimal.ZERO) < 0) {
                auditoriaService.registrarAcao("QUEBRA_DE_CAIXA", operadorLogado.getNome(), msgAuditoria);
                log.warn("QUEBRA DETECTADA: {}", msgAuditoria);
            } else {
                auditoriaService.registrarAcao("SOBRA_DE_CAIXA", operadorLogado.getNome(), msgAuditoria);
                log.info("SOBRA DETECTADA: {}", msgAuditoria);
            }

            if (caixaAuditorIaService != null) {
                caixaAuditorIaService.auditarQuebraDeCaixa(caixa.getId(), operadorLogado.getNome(), justificativa);
            }

        } else {
            auditoriaService.registrarAcao("FECHAMENTO_CAIXA", operadorLogado.getNome(), msgAuditoria);
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
            paginaCaixas = caixaDiarioRepository.findByDataAberturaBetweenComUsuario(
                    inicio.atStartOfDay(),
                    fim.atTime(23, 59, 59),
                    pageable
            );
        } else {
            paginaCaixas = caixaDiarioRepository.findAllComUsuario(pageable);
        }

        return paginaCaixas.map(this::converterParaDTOCompleto);
    }

    // ==================================================================================
    //  MÉTODOS DE AUDITORIA E ALERTAS
    // ==================================================================================
    @Transactional(readOnly = true)
    public List<CaixaDiarioDTO> buscarAlertasRiscoDashboard() {
        return caixaDiarioRepository.findAll().stream()
                .filter(c -> c.getAnaliseAuditoriaIa() != null &&
                        (c.getAnaliseAuditoriaIa().contains("[RISCO: ALTO]") || c.getAnaliseAuditoriaIa().contains("[RISCO: MEDIO]")))
                .map(this::converterParaDTOCompleto)
                .toList();
    }
}