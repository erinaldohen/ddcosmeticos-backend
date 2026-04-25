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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FinanceiroService {

    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private FornecedorRepository fornecedorRepository;

    @Autowired private CaixaService caixaService;
    @Autowired @Lazy private ContaReceberService contaReceberService;
    @Autowired @Lazy private ContaPagarService contaPagarService;

    @Transactional
    public void lancarReceitaDeVenda(Long vendaId, BigDecimal valorTotalVenda, String formaPagamentoStr, int parcelas, Long clienteId) {
        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada"));

        boolean ehAvista = isPagamentoAvista(formaPagamentoStr);

        BigDecimal valorPorParcela = valorTotalVenda.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        BigDecimal resto = valorTotalVenda.subtract(valorPorParcela.multiply(BigDecimal.valueOf(parcelas)));

        for (int i = 1; i <= parcelas; i++) {
            ContaReceber conta = new ContaReceber();
            conta.setVenda(venda);
            conta.setCliente(venda.getCliente());

            BigDecimal valorDestaParcela = valorPorParcela;
            if (i == parcelas) {
                valorDestaParcela = valorDestaParcela.add(resto);
            }

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

        BigDecimal valorPorParcela = valorTotalCompra.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        BigDecimal resto = valorTotalCompra.subtract(valorPorParcela.multiply(BigDecimal.valueOf(parcelas)));

        for (int i = 1; i <= parcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);
            conta.setDataEmissao(LocalDate.now());
            conta.setDataVencimento(LocalDate.now().plusMonths(i));
            conta.setStatus(StatusConta.PENDENTE);
            conta.setDescricao("Compra Produtos (" + observacao + ") - Parc. " + i + "/" + parcelas);

            BigDecimal valorDestaParcela = valorPorParcela;
            if (i == parcelas) {
                valorDestaParcela = valorDestaParcela.add(resto);
            }
            conta.setValorTotal(valorDestaParcela);
            conta.setValorPago(BigDecimal.ZERO);

            contaPagarRepository.save(conta);
        }
    }

    @Transactional
    public void registrarMovimentacaoManual(MovimentacaoDTO dto, String usuarioResponsavel) {
        MovimentacaoCaixa mov = new MovimentacaoCaixa();
        mov.setTipo(dto.getTipo());
        mov.setValor(dto.getValor());
        mov.setMotivo(dto.getMotivo());
        mov.setDataHora(LocalDateTime.now());
        mov.setUsuarioResponsavel(usuarioResponsavel);
        mov.setFormaPagamento(FormaDePagamento.DINHEIRO);

        caixaService.salvarMovimentacao(mov);
    }

    @Transactional
    public void darBaixaContaReceber(Long contaReceberId, BigDecimal valorPago) {
        ContaReceberDTO.BaixaTituloDTO dto = new ContaReceberDTO.BaixaTituloDTO(
                valorPago,
                FormaDePagamento.DINHEIRO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.now()
        );
        contaReceberService.baixarTitulo(contaReceberId, dto);
    }

    @Transactional
    public void darBaixaContaPagar(Long contaPagarId, BigDecimal valorPago) {
        ContaPagarDTO.BaixaContaPagarDTO dto = new ContaPagarDTO.BaixaContaPagarDTO(
                valorPago,
                FormaDePagamento.DINHEIRO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.now()
        );
        contaPagarService.pagarConta(contaPagarId, dto);
    }

    @Transactional(readOnly = true)
    public FechamentoCaixaDTO gerarResumoFechamento(LocalDate data) {
        List<ContaReceber> recebimentos = contaReceberRepository.findByDataPagamentoAndStatus(data, StatusConta.PAGO);
        List<ContaPagar> pagamentos = contaPagarRepository.findByDataPagamentoAndStatus(data, StatusConta.PAGO);

        BigDecimal totalEntradas = recebimentos.stream()
                .map(c -> c.getValorPago() != null ? c.getValorPago() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSaidas = pagamentos.stream()
                .map(p -> p.getValorPago() != null ? p.getValorPago() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saldoDinheiro = totalEntradas.subtract(totalSaidas).max(BigDecimal.ZERO);

        // 🔥 O DTO ATUALIZADO (Sem o mapa e sem variáveis não declaradas)
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
                .saldoEsperadoDinheiroGaveta(saldoDinheiro) // ✅ Corrigido (substitui o algumValor)
                .fechamentoCegoAtivo(false)
                .mensagemSistema("Resumo Global Financeiro")
                .build();
    }

    private boolean isPagamentoAvista(String forma) {
        if (forma == null) return false;
        String f = forma.toUpperCase();
        return f.contains("DINHEIRO") || f.contains("PIX") || f.contains("DEBITO");
    }
}