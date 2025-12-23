package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor // Gera o construtor para as variáveis 'final'
public class FinanceiroService {

    // DECLARAÇÕES OBRIGATÓRIAS PARA TIER 1
    private final ContaReceberRepository contaReceberRepository;
    private final ContaPagarRepository contaPagarRepository; // Resolvendo o erro de símbolo
    private final MovimentacaoCaixaRepository movimentacaoCaixaRepository;

    /**
     * Lança despesa originada de uma compra de estoque.
     */
    @Transactional
    public void lancarDespesaDeCompra(
            Fornecedor fornecedor,
            BigDecimal valorTotal,
            String numeroNota,
            FormaDePagamento forma,
            int parcelas,
            LocalDate vencimento) {

        log.info("Gerando Contas a Pagar - Fornecedor: {} | NF: {}", fornecedor.getRazaoSocial(), numeroNota);

        ContaPagar conta = new ContaPagar();
        conta.setFornecedor(fornecedor);
        conta.setValorTotal(valorTotal);
        conta.setDescricao("Compra de Estoque - NF: " + numeroNota);
        conta.setDataEmissao(LocalDate.now());
        conta.setDataVencimento(vencimento);
        conta.setCategoria("MERCADORIA_REVENDA");

        if (forma == FormaDePagamento.DINHEIRO || forma == FormaDePagamento.PIX) {
            conta.setStatus(StatusConta.PAGO);
            conta.setDataPagamento(LocalDate.now());
        } else {
            conta.setStatus(StatusConta.PENDENTE);
        }

        contaPagarRepository.save(conta);
    }

    /**
     * Registra entradas (Suprimento) ou saídas (Sangria) manuais.
     */
    @Transactional
    public MovimentacaoCaixa registarMovimentacaoManual(MovimentacaoDTO dto, String usuario) {
        MovimentacaoCaixa mov = new MovimentacaoCaixa();
        mov.setTipo(dto.tipo());
        mov.setValor(dto.valor());
        mov.setMotivo(dto.motivo());
        mov.setUsuarioResponsavel(usuario);
        return movimentacaoCaixaRepository.save(mov);
    }

    /**
     * Resumo de fechamento diário.
     */
    @Transactional(readOnly = true)
    public FechamentoCaixaDTO gerarResumoFechamento(LocalDate data) {
        var titulos = contaReceberRepository.findByDataEmissaoAndStatusNot(
                data, StatusConta.CANCELADO);

        var movs = movimentacaoCaixaRepository.findByDataHoraBetween(
                data.atStartOfDay(), data.atTime(LocalTime.MAX));

        BigDecimal suprimentos = somarPorTipo(movs, TipoMovimentacaoCaixa.SUPRIMENTO);
        BigDecimal sangrias = somarPorTipo(movs, TipoMovimentacaoCaixa.SANGRIA);

        BigDecimal vendasDinheiro = titulos.stream()
                .filter(t -> t.getFormaPagamento().equalsIgnoreCase("DINHEIRO"))
                .map(ContaReceber::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return FechamentoCaixaDTO.builder()
                .data(data)
                .quantidadeVendas(titulos.size())
                .totalSuprimentos(suprimentos)
                .totalSangrias(sangrias)
                .saldoFinalDinheiroEmEspecie(vendasDinheiro.add(suprimentos).subtract(sangrias))
                .build();
    }

    private BigDecimal somarPorTipo(List<MovimentacaoCaixa> movs, TipoMovimentacaoCaixa tipo) {
        return movs.stream()
                .filter(m -> m.getTipo() == tipo)
                .map(MovimentacaoCaixa::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void lancarReceitaDeVenda(Long vendaId, BigDecimal valorTotal, String formaPagamento) {
        ContaReceber titulo = new ContaReceber();
        titulo.setIdVendaRef(vendaId);
        titulo.setValorTotal(valorTotal);
        titulo.setValorLiquido(valorTotal);
        titulo.setDataEmissao(LocalDate.now());
        titulo.setFormaPagamento(formaPagamento);
        titulo.setStatus(formaPagamento.equalsIgnoreCase("DINHEIRO") ? StatusConta.RECEBIDO : StatusConta.PENDENTE);
        contaReceberRepository.save(titulo);
    }
    /**
     * Localiza todos os títulos financeiros vinculados a uma venda e marca-os como CANCELADOS.
     * Essencial para o processo de estorno de venda.
     */
    @Transactional
    public void cancelarReceitaVenda(Long vendaId) {
        log.info("Iniciando cancelamento financeiro da Venda #{}", vendaId);

        // Procura os títulos (Dinheiro, Cartão, etc.) associados a este ID de venda
        List<ContaReceber> titulos = contaReceberRepository.findByIdVendaRef(vendaId);

        if (titulos.isEmpty()) {
            log.warn("Nenhum título financeiro encontrado para cancelar na Venda #{}", vendaId);
            return;
        }

        // Altera o status de cada título para CANCELADO
        titulos.forEach(titulo -> {
            titulo.setStatus(StatusConta.CANCELADO);
            contaReceberRepository.save(titulo);
        });

        log.info("Sucesso: {} títulos da Venda #{} foram cancelados.", titulos.size(), vendaId);
    }
    /**
     * Ajusta o valor dos títulos financeiros em caso de devolução parcial.
     * Garante que o Contas a Receber reflita apenas o valor real que permaneceu na loja.
     */
    @Transactional
    public void ajustarReceitaPorDevolucao(Long vendaId, BigDecimal valorAbatimento) {
        log.info("Processando abatimento financeiro de R$ {} para a Venda #{}", valorAbatimento, vendaId);

        // 1. Localiza os títulos (recebíveis) vinculados à venda original
        List<ContaReceber> titulos = contaReceberRepository.findByIdVendaRef(vendaId);

        if (titulos.isEmpty()) {
            log.error("Tentativa de ajuste em venda sem títulos financeiros: #{}", vendaId);
            return;
        }

        // 2. Aplica o abatimento no valor total do título
        for (ContaReceber titulo : titulos) {
            BigDecimal valorAtual = titulo.getValorTotal() != null ? titulo.getValorTotal() : BigDecimal.ZERO;
            BigDecimal novoValor = valorAtual.subtract(valorAbatimento);

            // Proteção: o valor não pode ser negativo (regra de negócio DD Cosméticos)
            if (novoValor.compareTo(BigDecimal.ZERO) < 0) {
                novoValor = BigDecimal.ZERO;
            }

            titulo.setValorTotal(novoValor);
            titulo.setValorLiquido(novoValor); // O valor líquido é ajustado para bater com o novo total

            contaReceberRepository.save(titulo);
        }

        log.info("Ajuste de devolução concluído com sucesso para a Venda #{}", vendaId);
    }
}