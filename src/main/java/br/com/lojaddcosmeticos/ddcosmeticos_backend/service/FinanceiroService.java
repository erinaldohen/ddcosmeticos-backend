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

    // Injeção dos serviços especializados para garantir integridade e evitar duplicação de regra
    @Autowired private CaixaService caixaService;
    @Autowired @Lazy private ContaReceberService contaReceberService; // Lazy para evitar ciclo se houver
    @Autowired @Lazy private ContaPagarService contaPagarService;

    /**
     * Gera as parcelas no Contas a Receber a partir de uma Venda.
     */
    @Transactional
    public void lancarReceitaDeVenda(Long vendaId, BigDecimal valorTotalVenda, String formaPagamentoStr, int parcelas, Long clienteId) {
        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new RuntimeException("Venda não encontrada"));

        // Define se é à vista ou a prazo
        boolean ehAvista = isPagamentoAvista(formaPagamentoStr);

        // Se for à vista, o dinheiro já entra no caixa pelo PDV/CaixaService no momento da venda.
        // Opcional: Se você quiser registrar um "Titulo Pago" para histórico, pode.
        // Aqui assumiremos que vendas parceladas geram títulos pendentes.

        BigDecimal valorPorParcela = valorTotalVenda.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        BigDecimal resto = valorTotalVenda.subtract(valorPorParcela.multiply(BigDecimal.valueOf(parcelas)));

        for (int i = 1; i <= parcelas; i++) {
            ContaReceber conta = new ContaReceber();
            conta.setVenda(venda);
            conta.setCliente(venda.getCliente());

            // Ajusta o valor da última parcela com o resto da divisão
            BigDecimal valorDestaParcela = valorPorParcela;
            if (i == parcelas) {
                valorDestaParcela = valorDestaParcela.add(resto);
            }

            conta.setValorTotal(valorDestaParcela);
            conta.setValorPago(BigDecimal.ZERO); // Começa zerado

            conta.setDataEmissao(LocalDate.now());
            // Vencimento: 30 dias após para cada parcela
            conta.setDataVencimento(LocalDate.now().plusMonths(i));

            // Se for Dinheiro/Pix no ato, já nasce paga (Apenas para registro contábil)
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

        // Busca usando o objeto Venda, conforme mapeamento JPA correto
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
            conta.setDataVencimento(LocalDate.now().plusMonths(i)); // 1 mês para o primeiro vencimento
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
        // Delega para o CaixaService para garantir que o saldo do caixa seja atualizado
        MovimentacaoCaixa mov = new MovimentacaoCaixa();
        mov.setTipo(dto.getTipo());
        mov.setValor(dto.getValor());
        mov.setMotivo(dto.getMotivo());
        mov.setDataHora(LocalDateTime.now());
        mov.setUsuarioResponsavel(usuarioResponsavel);

        // Se não informar forma de pagamento (ex: sangria é sempre dinheiro), assume null ou padrão
        mov.setFormaPagamento(FormaDePagamento.DINHEIRO);

        caixaService.salvarMovimentacao(mov);
    }

    @Transactional
    public void darBaixaContaReceber(Long contaReceberId, BigDecimal valorPago) {
        // Delega para o serviço especialista que já tem a lógica de caixa e validação
        ContaReceberDTO.BaixaTituloDTO dto = new ContaReceberDTO.BaixaTituloDTO(
                valorPago,
                FormaDePagamento.DINHEIRO, // Padrão se não informado
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDate.now()
        );
        contaReceberService.baixarTitulo(contaReceberId, dto);
    }

    @Transactional
    public void darBaixaContaPagar(Long contaPagarId, BigDecimal valorPago) {
        // Delega para o serviço especialista
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
        // Nota: Este método calcula baseado em PAGAMENTOS de títulos.
        // O ideal para fechamento de caixa é olhar para a tabela MovimentacaoCaixa (via CaixaService).
        // Mantido aqui para compatibilidade de relatórios gerenciais globais.

        List<ContaReceber> recebimentos = contaReceberRepository.findByDataPagamentoAndStatus(data, StatusConta.PAGO);
        List<ContaPagar> pagamentos = contaPagarRepository.findByDataPagamentoAndStatus(data, StatusConta.PAGO);

        // Como ContaReceber não tem campo formaPagamento (está na Venda ou Movimentação),
        // essa lógica é aproximada ou precisaria de join com MovimentacaoCaixa.
        // Simplificando assumindo que tudo foi dinheiro para evitar erro de compilação,
        // mas o ideal é refatorar para usar MovimentacaoCaixaRepository.

        BigDecimal totalEntradas = recebimentos.stream()
                .map(c -> c.getValorPago() != null ? c.getValorPago() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSaidas = pagamentos.stream()
                .map(p -> p.getValorPago() != null ? p.getValorPago() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> formas = new HashMap<>();
        formas.put("TOTAL_GERAL", totalEntradas);

        return FechamentoCaixaDTO.builder()
                .data(data)
                .quantidadeVendas((long) recebimentos.size())
                .totalVendasBruto(totalEntradas)
                .totalSangrias(totalSaidas)
                .totalSuprimentos(BigDecimal.ZERO)
                .totaisPorFormaPagamento(formas)
                .saldoFinalDinheiroEmEspecie(totalEntradas.subtract(totalSaidas))
                .build();
    }

    // --- Auxiliares ---

    private boolean isPagamentoAvista(String forma) {
        if (forma == null) return false;
        String f = forma.toUpperCase();
        return f.contains("DINHEIRO") || f.contains("PIX") || f.contains("DEBITO");
    }
}