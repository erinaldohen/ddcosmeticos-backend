package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
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

@Service
public class CaixaService {

    @Autowired private CaixaDiarioRepository caixaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private MovimentacaoCaixaRepository movimentacaoRepository; // Crie este repo vazio se nao tiver

    @Transactional
    public CaixaDiario abrirCaixa(BigDecimal fundoTroco) {
        // 1. Garante que o usuário está realmente autenticado e existe no banco
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
        }

        Usuario operador = usuarioRepository.findByMatricula(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado no banco."));

        // 2. Verifica se já existe caixa aberto (usando findFirst para evitar erro de duplicidade)
        if (caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Você já possui um caixa aberto.");
        }

        try {
            CaixaDiario caixa = new CaixaDiario();
            caixa.setUsuarioAbertura(operador);
            caixa.setDataAbertura(LocalDateTime.now());
            caixa.setSaldoInicial(fundoTroco != null ? fundoTroco : BigDecimal.ZERO);
            caixa.setStatus(StatusCaixa.ABERTO);

            // 3. Salva e força o commit para ver se o erro acontece aqui
            return caixaRepository.saveAndFlush(caixa);
        } catch (Exception e) {
            // Isso vai imprimir o erro real no console do seu Java
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erro ao salvar caixa: " + e.getMessage());
        }
    }

    @Transactional
    public CaixaDiario fecharCaixa(BigDecimal saldoInformado) {
        if (saldoInformado == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O saldo final informado é obrigatório.");
        }

        Usuario operador = getUsuarioLogado();
        CaixaDiario caixa = caixaRepository.findFirstByUsuarioAberturaAndStatus(operador, StatusCaixa.ABERTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não há caixa aberto para este usuário."));

        LocalDateTime agora = LocalDateTime.now();

        // 1. Busca todas as vendas feitas pelo usuário neste período
        List<Venda> vendas = vendaRepository.buscarVendasDoUsuarioNoPeriodo(
                operador.getId(),
                caixa.getDataAbertura(),
                agora
        );

        // 2. Calcula Totais por Forma de Pagamento
        BigDecimal totalDinheiro = somarPorForma(vendas, FormaDePagamento.DINHEIRO);
        BigDecimal totalPix = somarPorForma(vendas, FormaDePagamento.PIX);
        BigDecimal totalCartao = somarPorForma(vendas, FormaDePagamento.CREDITO)
                .add(somarPorForma(vendas, FormaDePagamento.DEBITO));

        // 3. Soma Sangrias e Suprimentos (Garante lista não nula)
        BigDecimal sangrias = BigDecimal.ZERO;
        BigDecimal suprimentos = BigDecimal.ZERO;

        if (caixa.getMovimentacoes() != null) {
            sangrias = caixa.getMovimentacoes().stream()
                    .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SANGRIA)
                    .map(MovimentacaoCaixa::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            suprimentos = caixa.getMovimentacoes().stream()
                    .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SUPRIMENTO)
                    .map(MovimentacaoCaixa::getValor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // 4. Saldo Calculado (O que DEVERIA ter na gaveta em DINHEIRO)
        BigDecimal saldoEsperado = caixa.getSaldoInicial()
                .add(totalDinheiro)
                .add(suprimentos)
                .subtract(sangrias);

        // 5. Atualiza o objeto Caixa
        caixa.setSaldoFinalCalculado(saldoEsperado);
        caixa.setSaldoFinalInformado(saldoInformado);
        caixa.setDiferenca(saldoInformado.subtract(saldoEsperado));

        caixa.setTotalVendasDinheiro(totalDinheiro);
        caixa.setTotalVendasPix(totalPix);
        caixa.setTotalVendasCartao(totalCartao); // Certifique-se que este campo existe na Model
        caixa.setTotalSangrias(sangrias);
        caixa.setTotalSuprimentos(suprimentos);

        caixa.setDataFechamento(agora);
        caixa.setUsuarioFechamento(operador);
        caixa.setStatus(StatusCaixa.FECHADO);

        return caixaRepository.save(caixa);
    }

    // Método auxiliar para limpar o código de somas
    private BigDecimal somarPorForma(List<Venda> vendas, FormaDePagamento forma) {
        return vendas.stream()
                .filter(v -> v.getFormaDePagamento() == forma)
                .map(Venda::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Método auxiliar para pegar usuário do Spring Security
    private Usuario getUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String login = auth.getName();

        return usuarioRepository.findByMatricula(login)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, // <--- MUDAMOS PARA 401
                        "Sessão inválida ou usuário não encontrado. Faça login novamente."
                ));
    }
}