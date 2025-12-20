package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FinanceiroService {

    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private MovimentacaoCaixaRepository movimentacaoCaixaRepository; // INJETADO PARA CORRIGIR LINHA 195

    /**
     * Gera o resumo financeiro completo do dia (Vendas + Sangrias + Suprimentos).
     */
    @Transactional(readOnly = true)
    public FechamentoCaixaDTO gerarResumoFechamento(LocalDate data) {
        // 1. Busca títulos do dia (exceto cancelados)
        List<ContaReceber> titulos = contaReceberRepository.findByDataEmissaoAndStatusNot(data, StatusConta.CANCELADO);

        // 2. Busca Sangrias e Suprimentos
        List<MovimentacaoCaixa> movs = movimentacaoCaixaRepository.findByDataHoraBetween(
                data.atStartOfDay(), data.atTime(LocalTime.MAX)
        );

        BigDecimal suprimentos = movs.stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SUPRIMENTO)
                .map(MovimentacaoCaixa::getValor) // CORREÇÃO DE SINTAXE (LINHA 207)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sangrias = movs.stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoCaixa.SANGRIA)
                .map(MovimentacaoCaixa::getValor) // CORREÇÃO DE SINTAXE (LINHA 208)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Cálculo de Dinheiro em Espécie (Vendas em Dinheiro + Suprimentos - Sangrias)
        BigDecimal vendasDinheiro = titulos.stream()
                .filter(t -> t.getFormaPagamento().equalsIgnoreCase("DINHEIRO"))
                .map(ContaReceber::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoEspecie = vendasDinheiro.add(suprimentos).subtract(sangrias);

        return new FechamentoCaixaDTO(
                data,
                titulos.size(),
                calcularTotalBruto(titulos), // MÉTODO AUXILIAR ABAIXO
                suprimentos,
                sangrias,
                agruparPorForma(titulos),    // MÉTODO AUXILIAR ABAIXO
                saldoEspecie
        );
    }

    @Transactional
    public String validarEFecharCaixa(ConfirmacaoFechamentoDTO dto) {
        FechamentoCaixaDTO resumoSistema = gerarResumoFechamento(dto.data());
        BigDecimal saldoEsperado = resumoSistema.saldoFinalDinheiroEmEspecie();

        BigDecimal diferenca = dto.valorContadoEmEspecie().subtract(saldoEsperado).abs();

        if (diferenca.compareTo(new BigDecimal("5.00")) > 0) {
            if (dto.justificativaDiferenca() == null || dto.justificativaDiferenca().isBlank()) {
                throw new ValidationException("Diferença de caixa crítica (R$ " + diferenca + "). Justificativa obrigatória.");
            }
            log.warn("Quebra de caixa de R$ {} justificada por {}", diferenca, dto.justificativaDiferenca());
        }

        return "Caixa fechado com sucesso.";
    }

    @Transactional
    public void lancarReceitaDeVenda(Long vendaId, BigDecimal valorTotal, String formaPagamento) {
        ContaReceber titulo = new ContaReceber();
        titulo.setIdVendaRef(vendaId);
        titulo.setValorTotal(valorTotal);
        titulo.setValorLiquido(valorTotal);
        titulo.setDataEmissao(LocalDate.now());
        titulo.setFormaPagamento(formaPagamento);

        if (formaPagamento.equalsIgnoreCase("DINHEIRO") || formaPagamento.equalsIgnoreCase("PIX")) {
            titulo.setDataVencimento(LocalDate.now());
            titulo.setStatus(StatusConta.RECEBIDO);
        } else {
            titulo.setDataVencimento(LocalDate.now().plusDays(1));
            titulo.setStatus(StatusConta.PENDENTE);
        }
        contaReceberRepository.save(titulo);
    }

    @Transactional
    public void lancarDespesaDeCompra(Fornecedor fornecedor, BigDecimal valorTotal, String numeroNota, FormaPagamento forma, int parcelas, LocalDate vencimento) {
        ContaPagar conta = new ContaPagar();
        conta.setFornecedor(fornecedor);
        conta.setValorTotal(valorTotal);
        conta.setDescricao("Compra Estoque - NF: " + numeroNota);
        conta.setDataEmissao(LocalDate.now());
        conta.setDataVencimento(vencimento);
        conta.setStatus(StatusConta.PENDENTE);
        contaPagarRepository.save(conta);
    }

    // --- MÉTODOS AUXILIARES PARA RESOLVER LINHAS 212 E 213 ---

    private BigDecimal calcularTotalBruto(List<ContaReceber> titulos) {
        return titulos.stream()
                .map(ContaReceber::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> agruparPorForma(List<ContaReceber> titulos) {
        return titulos.stream()
                .collect(Collectors.groupingBy(
                        ContaReceber::getFormaPagamento,
                        Collectors.reducing(BigDecimal.ZERO, ContaReceber::getValorTotal, BigDecimal::add)
                ));
    }

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
     * Localiza todos os títulos de receita (Dinheiro, PIX, Cartão)
     * vinculados a uma venda e os marca como CANCELADOS.
     */
    @Transactional
    public void cancelarReceitaVenda(Long vendaId) {
        log.info("Cancelando títulos financeiros da Venda #{}", vendaId);

        // Busca os títulos vinculados ao ID da venda
        List<ContaReceber> titulos = contaReceberRepository.findByIdVendaRef(vendaId);

        if (titulos.isEmpty()) {
            log.warn("Nenhum título financeiro encontrado para a Venda #{}", vendaId);
            return;
        }

        for (ContaReceber titulo : titulos) {
            titulo.setStatus(StatusConta.CANCELADO);
            // Opcional: registrar a data do cancelamento se o seu modelo permitir
            contaReceberRepository.save(titulo);
        }
    }

    /**
     * Ajusta o valor dos títulos financeiros em caso de devolução parcial.
     * @param vendaId ID da venda original
     * @param valorAbatimento Valor total dos itens que estão sendo devolvidos
     */
    @Transactional
    public void ajustarReceitaPorDevolucao(Long vendaId, BigDecimal valorAbatimento) {
        log.info("Processando abatimento financeiro de R$ {} para a Venda #{}", valorAbatimento, vendaId);

        // 1. Busca os títulos vinculados à venda
        List<ContaReceber> titulos = contaReceberRepository.findByIdVendaRef(vendaId);

        if (titulos.isEmpty()) {
            throw new ResourceNotFoundException("Nenhum registro financeiro encontrado para a venda #" + vendaId);
        }

        // 2. Aplica o abatimento (geralmente no primeiro título ou proporcionalmente)
        // Para simplificar a DD Cosméticos, subtraímos do montante total
        for (ContaReceber titulo : titulos) {
            BigDecimal novoValor = titulo.getValorTotal().subtract(valorAbatimento);

            // Proteção contra valores negativos
            if (novoValor.compareTo(BigDecimal.ZERO) < 0) novoValor = BigDecimal.ZERO;

            titulo.setValorTotal(novoValor);
            titulo.setValorLiquido(novoValor); // Líquido = Total (regra da maquineta externa)

            contaReceberRepository.save(titulo);
        }

        log.info("Ajuste financeiro concluído para a Venda #{}", vendaId);
    }
}