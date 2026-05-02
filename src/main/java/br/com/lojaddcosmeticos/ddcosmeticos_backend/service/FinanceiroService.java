package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro.ContaPagarDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro.ContaReceberDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceiroService {

    private final ContaReceberRepository contaReceberRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final VendaRepository vendaRepository;
    private final ClienteRepository clienteRepository;
    private final FornecedorRepository fornecedorRepository;

    private final CaixaService caixaService;

    @Lazy private final ContaReceberService contaReceberService;
    @Lazy private final ContaPagarService contaPagarService;

    @Transactional
    public void lancarReceitaDeVenda(Long vendaId, BigDecimal valorTotalVenda, String formaPagamentoStr, int parcelas, Long clienteId) {
        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada"));

        boolean ehAvista = isPagamentoAvista(formaPagamentoStr);
        BigDecimal valorTotal = nvl(valorTotalVenda);
        BigDecimal valorPorParcela = valorTotal.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        BigDecimal resto = valorTotal.subtract(valorPorParcela.multiply(BigDecimal.valueOf(parcelas)));

        for (int i = 1; i <= parcelas; i++) {
            ContaReceber conta = new ContaReceber();
            conta.setVenda(venda);
            conta.setCliente(venda.getCliente());

            BigDecimal valorDestaParcela = (i == parcelas) ? valorPorParcela.add(resto) : valorPorParcela;

            conta.setValorTotal(valorDestaParcela);
            conta.setValorPago(BigDecimal.ZERO);
            conta.setDataEmissao(LocalDate.now());
            conta.setDataVencimento(LocalDate.now().plusMonths(i));

            if (ehAvista) {
                conta.setStatus(StatusConta.PAGO);
                conta.setValorPago(valorDestaParcela);
                conta.setDataPagamento(LocalDate.now());
                conta.setDataVencimento(LocalDate.now());
            } else {
                conta.setStatus(StatusConta.PENDENTE);
            }

            contaReceberRepository.save(conta);
        }
    }

    @Transactional
    public void cancelarReceitaDeVenda(Long vendaId) {
        Venda venda = new Venda();
        venda.setIdVenda(vendaId);
        List<ContaReceber> contas = contaReceberRepository.findByVenda(venda);
        contaReceberRepository.deleteAll(contas);
    }

    @Transactional
    public void lancarDespesaDeCompra(Long produtoId, Long fornecedorId, BigDecimal valorTotalCompra, int parcelas, String observacao) {
        Fornecedor fornecedor = fornecedorRepository.findById(fornecedorId)
                .orElseThrow(() -> new RuntimeException("Fornecedor não encontrado"));

        BigDecimal valorTotal = nvl(valorTotalCompra);
        BigDecimal valorPorParcela = valorTotal.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        BigDecimal resto = valorTotal.subtract(valorPorParcela.multiply(BigDecimal.valueOf(parcelas)));

        for (int i = 1; i <= parcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);
            conta.setDataEmissao(LocalDate.now());
            conta.setDataVencimento(LocalDate.now().plusMonths(i));
            conta.setStatus(StatusConta.PENDENTE);
            conta.setDescricao("Compra Produtos (" + observacao + ") - Parc. " + i + "/" + parcelas);

            conta.setValorTotal((i == parcelas) ? valorPorParcela.add(resto) : valorPorParcela);
            conta.setValorPago(BigDecimal.ZERO);

            contaPagarRepository.save(conta);
        }
    }

    @Transactional
    public void registrarMovimentacaoManual(MovimentacaoDTO dto, String usuarioResponsavel) {
        MovimentacaoCaixa mov = new MovimentacaoCaixa();
        mov.setTipo(dto.getTipo());
        mov.setValor(nvl(dto.getValor()));
        mov.setMotivo(dto.getMotivo());
        mov.setDataHora(LocalDateTime.now());
        mov.setUsuarioResponsavel(usuarioResponsavel);
        mov.setFormaPagamento(FormaDePagamento.DINHEIRO);

        caixaService.salvarMovimentacao(mov);
    }

    @Transactional
    public void darBaixaContaReceber(Long contaReceberId, BigDecimal valorPago) {
        contaReceberService.baixarTitulo(contaReceberId, new ContaReceberDTO.BaixaTituloDTO(
                nvl(valorPago), FormaDePagamento.DINHEIRO, BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now()));
    }

    @Transactional
    public void darBaixaContaPagar(Long contaPagarId, BigDecimal valorPago) {
        contaPagarService.pagarConta(contaPagarId, new ContaPagarDTO.BaixaContaPagarDTO(
                nvl(valorPago), FormaDePagamento.DINHEIRO, BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.now()));
    }

    @Transactional(readOnly = true)
    public FechamentoCaixaDTO gerarResumoFechamento(LocalDate data) {

        // 🚨 CORREÇÃO DEFINITIVA LINHA 137:
        // Passamos 'Pageable.unpaged()' e pedimos '.getContent()' para obter a List nativa sem ambiguidades de sobrecarga.
        List<ContaReceber> recebimentos = contaReceberRepository.findByDataPagamentoAndStatus(data, StatusConta.PAGO, Pageable.unpaged()).getContent();
        List<ContaPagar> pagamentos = contaPagarRepository.findByDataPagamentoAndStatus(data, StatusConta.PAGO, Pageable.unpaged()).getContent();

        BigDecimal totalEntradas = recebimentos.stream()
                .map(c -> nvl(c.getValorPago()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSaidas = pagamentos.stream()
                .map(p -> nvl(p.getValorPago()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoDinheiro = totalEntradas.subtract(totalSaidas).max(BigDecimal.ZERO);

        return FechamentoCaixaDTO.builder()
                .dataAbertura(data.atStartOfDay())
                .dataFechamento(LocalDateTime.now())
                .quantidadeVendas((long) recebimentos.size())
                .totalVendasBruto(totalEntradas)
                .totalSangrias(totalSaidas)
                .totalSuprimentos(BigDecimal.ZERO)
                .totalVendasDinheiro(totalEntradas)
                .totalVendasPix(BigDecimal.ZERO)
                .totalVendasCredito(BigDecimal.ZERO)
                .totalVendasDebito(BigDecimal.ZERO)
                .saldoEsperadoDinheiroGaveta(saldoDinheiro)
                .fechamentoCegoAtivo(false)
                .mensagemSistema("Resumo Global Financeiro")
                .build();
    }

    private boolean isPagamentoAvista(String forma) {
        if (forma == null) return false;
        String f = forma.toUpperCase();
        return f.contains("DINHEIRO") || f.contains("PIX") || f.contains("DEBITO");
    }

    private BigDecimal nvl(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}