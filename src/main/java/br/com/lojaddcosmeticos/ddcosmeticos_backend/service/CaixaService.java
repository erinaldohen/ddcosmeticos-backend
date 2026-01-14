package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO; // IMPORT NOVO
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class CaixaService {

    @Autowired private CaixaDiarioRepository caixaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private MovimentacaoCaixaRepository movimentacaoRepository;

    // --- NOVO MÉTODO: BUSCAR STATUS PARA O DASHBOARD ---
    public CaixaDiarioDTO buscarStatusAtual() {
        Usuario operador = getUsuarioLogado();

        // Busca se tem caixa aberto
        Optional<CaixaDiario> caixaOpt = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO);

        if (caixaOpt.isEmpty()) {
            // Se não tiver caixa aberto, retorna null ou um DTO vazio indicando fechado
            return null;
        }

        CaixaDiario caixa = caixaOpt.get();

        // CALCULA TOTAIS EM TEMPO REAL (Para o gráfico do Dashboard ficar atualizado)
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

    @Transactional
    public CaixaDiario abrirCaixa(BigDecimal fundoTroco) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        }

        Usuario operador = usuarioRepository.findByMatricula(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));

        if (caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Você já possui um caixa aberto.");
        }

        CaixaDiario caixa = new CaixaDiario();
        caixa.setUsuarioAbertura(operador);
        caixa.setDataAbertura(LocalDateTime.now());
        caixa.setSaldoInicial(fundoTroco != null ? fundoTroco : BigDecimal.ZERO);
        caixa.setStatus(StatusCaixa.ABERTO);

        return caixaRepository.save(caixa);
    }

    @Transactional
    public CaixaDiario fecharCaixa(BigDecimal saldoInformado) {
        if (saldoInformado == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo final obrigatório.");

        Usuario operador = getUsuarioLogado();
        CaixaDiario caixa = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não há caixa aberto."));

        LocalDateTime agora = LocalDateTime.now();
        List<Venda> vendas = vendaRepository.buscarVendasDoUsuarioNoPeriodo(operador.getId(), caixa.getDataAbertura(), agora);

        BigDecimal totalDinheiro = somarPorForma(vendas, FormaDePagamento.DINHEIRO);
        BigDecimal totalPix = somarPorForma(vendas, FormaDePagamento.PIX);
        BigDecimal totalCartao = somarPorForma(vendas, FormaDePagamento.CREDITO).add(somarPorForma(vendas, FormaDePagamento.DEBITO));

        BigDecimal sangrias = BigDecimal.ZERO;
        BigDecimal suprimentos = BigDecimal.ZERO;

        if (caixa.getMovimentacoes() != null) {
            sangrias = caixa.getMovimentacoes().stream().filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SANGRIA)
                    .map(MovimentacaoCaixa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            suprimentos = caixa.getMovimentacoes().stream().filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SUPRIMENTO)
                    .map(MovimentacaoCaixa::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal saldoEsperado = caixa.getSaldoInicial().add(totalDinheiro).add(suprimentos).subtract(sangrias);

        caixa.setSaldoFinalCalculado(saldoEsperado);
        caixa.setSaldoFinalInformado(saldoInformado);
        caixa.setDiferenca(saldoInformado.subtract(saldoEsperado));
        caixa.setTotalVendasDinheiro(totalDinheiro);
        caixa.setTotalVendasPix(totalPix);
        caixa.setTotalVendasCartao(totalCartao);
        caixa.setTotalSangrias(sangrias);
        caixa.setTotalSuprimentos(suprimentos);
        caixa.setDataFechamento(agora);
        caixa.setUsuarioFechamento(operador);
        caixa.setStatus(StatusCaixa.FECHADO);

        return caixaRepository.save(caixa);
    }

    private BigDecimal somarPorForma(List<Venda> vendas, FormaDePagamento forma) {
        return vendas.stream().filter(v -> v.getFormaDePagamento() == forma)
                .map(Venda::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Usuario getUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return usuarioRepository.findByMatricula(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida."));
    }
}