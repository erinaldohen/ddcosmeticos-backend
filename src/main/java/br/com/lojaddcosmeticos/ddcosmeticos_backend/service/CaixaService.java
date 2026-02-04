package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentacaoCaixaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CaixaService {

    private final CaixaDiarioRepository caixaRepository;
    private final UsuarioRepository usuarioRepository;
    private final VendaRepository vendaRepository;
    private final MovimentacaoCaixaRepository movimentacaoRepository;

    // --- LISTAGEM DE MOVIMENTAÇÕES (USADO NO CONTROLLER) ---
    public List<MovimentacaoCaixa> buscarHistorico(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(23, 59, 59);
        return movimentacaoRepository.findByDataHoraBetween(dataInicio, dataFim);
    }

    // --- STATUS ATUAL (DTO) ---
    public CaixaDiarioDTO buscarStatusAtual() {
        Usuario operador = getUsuarioLogado();

        Optional<CaixaDiario> caixaOpt = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO);

        if (caixaOpt.isEmpty()) {
            return null; // Frontend entende que não tem caixa aberto
        }

        CaixaDiario caixa = caixaOpt.get();

        // Busca vendas desde a abertura até agora
        List<Venda> vendasHoje = vendaRepository.buscarVendasDoUsuarioNoPeriodo(
                operador.getId(),
                caixa.getDataAbertura(),
                LocalDateTime.now()
        );

        BigDecimal dinheiro = somarPorForma(vendasHoje, FormaDePagamento.DINHEIRO);
        BigDecimal pix = somarPorForma(vendasHoje, FormaDePagamento.PIX);
        BigDecimal cartao = somarPorForma(vendasHoje, FormaDePagamento.CREDITO)
                .add(somarPorForma(vendasHoje, FormaDePagamento.DEBITO));

        return new CaixaDiarioDTO(
                caixa.getId(),
                "ABERTO",
                caixa.getSaldoInicial(),
                dinheiro,
                pix,
                cartao
        );
    }

    // --- ABRIR CAIXA ---
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
        caixa.setStatus(StatusCaixa.ABERTO);

        // Inicializa acumuladores zerados
        caixa.setTotalVendasDinheiro(BigDecimal.ZERO);
        caixa.setTotalVendasPix(BigDecimal.ZERO);
        caixa.setTotalVendasCartao(BigDecimal.ZERO);
        caixa.setTotalEntradas(BigDecimal.ZERO);
        caixa.setTotalSaidas(BigDecimal.ZERO);

        return caixaRepository.save(caixa);
    }

    // --- FECHAR CAIXA (LÓGICA CRÍTICA) ---
    @Transactional
    public CaixaDiario fecharCaixa(BigDecimal saldoInformado) {
        if (saldoInformado == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo final (gaveta) obrigatório.");

        Usuario operador = getUsuarioLogado();
        CaixaDiario caixa = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não há caixa aberto para este usuário."));

        LocalDateTime agora = LocalDateTime.now();

        // 1. Calcula Vendas
        List<Venda> vendas = vendaRepository.buscarVendasDoUsuarioNoPeriodo(operador.getId(), caixa.getDataAbertura(), agora);

        BigDecimal totalDinheiro = somarPorForma(vendas, FormaDePagamento.DINHEIRO);
        BigDecimal totalPix = somarPorForma(vendas, FormaDePagamento.PIX);
        BigDecimal totalCartao = somarPorForma(vendas, FormaDePagamento.CREDITO)
                .add(somarPorForma(vendas, FormaDePagamento.DEBITO));

        // 2. Calcula Movimentações (Sangrias e Suprimentos)
        BigDecimal sangrias = BigDecimal.ZERO;
        BigDecimal suprimentos = BigDecimal.ZERO;

        List<MovimentacaoCaixa> movs = caixa.getMovimentacoes(); // Garantido pelo Model atualizado
        if (movs != null && !movs.isEmpty()) {
            sangrias = movs.stream()
                    .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SANGRIA)
                    .map(MovimentacaoCaixa::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            suprimentos = movs.stream()
                    .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SUPRIMENTO)
                    .map(MovimentacaoCaixa::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // 3. Fórmula do Saldo Esperado (Fundo + VendasDinheiro + Suprimentos - Sangrias)
        // Pix e Cartão não somam na gaveta física
        BigDecimal saldoEsperado = caixa.getSaldoInicial()
                .add(totalDinheiro)
                .add(suprimentos)
                .subtract(sangrias);

        // 4. Preenche o objeto com os dados finais
        caixa.setValorCalculadoSistema(saldoEsperado); // CORRIGIDO: Era 'saldoCalculado'
        caixa.setValorFechamento(saldoInformado);      // CORRIGIDO: Nome do campo no Model

        // Preenche os totais analíticos para o relatório
        caixa.setTotalVendasDinheiro(totalDinheiro);
        caixa.setTotalVendasPix(totalPix);
        caixa.setTotalVendasCartao(totalCartao);
        caixa.setTotalEntradas(suprimentos); // CORRIGIDO: Nome do campo no Model
        caixa.setTotalSaidas(sangrias);      // CORRIGIDO: Nome do campo no Model

        caixa.setDataFechamento(agora);
        caixa.setStatus(StatusCaixa.FECHADO);

        return caixaRepository.save(caixa);
    }

    // --- UTILS ---
    private BigDecimal somarPorForma(List<Venda> vendas, FormaDePagamento forma) {
        if (vendas == null || vendas.isEmpty()) return BigDecimal.ZERO;
        return vendas.stream()
                .filter(v -> v.getFormaDePagamento() == forma)
                .map(Venda::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

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