package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro.ContaPagarDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaPagar;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaPagarRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaPagarService {

    private final ContaPagarRepository repository;
    private final FornecedorRepository fornecedorRepository;
    private final CaixaService caixaService;

    // --- LEITURA BÁSICA ---

    @Transactional(readOnly = true)
    public List<ContaPagarDTO> listar(String statusStr, String termo) {
        return repository.findAll().stream()
                .filter(c -> filtrarPorStatus(c, statusStr))
                .filter(c -> filtrarPorTermo(c, termo))
                .sorted(Comparator.comparing(ContaPagar::getDataVencimento))
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ContaPagarDTO.ResumoPagarDTO obterResumo() {
        List<ContaPagar> todas = repository.findAll();
        LocalDate hoje = LocalDate.now();

        BigDecimal aPagar = todas.stream()
                .filter(c -> c.getStatus() != StatusConta.PAGO)
                .map(this::calcularRestanteSeguro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal vencido = todas.stream()
                .filter(c -> c.getStatus() != StatusConta.PAGO && c.getDataVencimento() != null && c.getDataVencimento().isBefore(hoje))
                .map(this::calcularRestanteSeguro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pagoHoje = todas.stream()
                .filter(c -> c.getDataPagamento() != null && c.getDataPagamento().isEqual(hoje))
                .map(c -> c.getValorPago() != null ? c.getValorPago() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ContaPagarDTO.ResumoPagarDTO(aPagar, vencido, pagoHoje);
    }

    // =========================================================================
    // INTELIGÊNCIA FINANCEIRA: ANÁLISE DE SAÚDE DA LOJA
    // =========================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> obterAnaliseInteligenteDoMes(int mes, int ano) {
        LocalDate inicioMes = LocalDate.of(ano, mes, 1);
        LocalDate fimMes = inicioMes.withDayOfMonth(inicioMes.lengthOfMonth());

        List<ContaPagar> contasDoMes = repository.findAll().stream()
                .filter(c -> c.getDataVencimento() != null &&
                        !c.getDataVencimento().isBefore(inicioMes) &&
                        !c.getDataVencimento().isAfter(fimMes))
                .collect(Collectors.toList());

        BigDecimal despesasFixas = BigDecimal.ZERO;
        BigDecimal despesasVariaveis = BigDecimal.ZERO;
        BigDecimal compraMercadorias = BigDecimal.ZERO;

        for (ContaPagar c : contasDoMes) {
            BigDecimal valor = c.getValorTotal() != null ? c.getValorTotal() : BigDecimal.ZERO;
            String desc = c.getDescricao() != null ? c.getDescricao().toLowerCase() : "";
            String forn = c.getFornecedor() != null && c.getFornecedor().getRazaoSocial() != null
                    ? c.getFornecedor().getRazaoSocial().toLowerCase() : "";

            // Motor de Categorização Automática (Lê o texto digitado pelo operador)
            if (desc.contains("aluguel") || desc.contains("luz") || desc.contains("energia") || desc.contains("celpe") ||
                    desc.contains("água") || desc.contains("agua") || desc.contains("internet") || desc.contains("telefone") ||
                    desc.contains("salário") || desc.contains("salario") || desc.contains("contador") || desc.contains("sistema") ||
                    desc.contains("iptu") || desc.contains("condomínio") || desc.contains("limpeza")) {

                despesasFixas = despesasFixas.add(valor);
            }
            else if (desc.contains("sacola") || desc.contains("embalagem") || desc.contains("brinde") ||
                    desc.contains("marketing") || desc.contains("anúncio") || desc.contains("instagram") || desc.contains("panfleto")) {

                despesasVariaveis = despesasVariaveis.add(valor);
            }
            else if (desc.contains("mercadoria") || desc.contains("produto") || desc.contains("estoque") || desc.contains("compra") ||
                    desc.contains("boleto") || forn.contains("cosmético") || forn.contains("distribuidora") ||
                    forn.contains("beleza") || forn.contains("indústria")) {

                compraMercadorias = compraMercadorias.add(valor);
            }
            else {
                // Se o sistema não souber o que é, joga na Despesa Fixa por segurança (Conservadorismo contábil)
                despesasFixas = despesasFixas.add(valor);
            }
        }

        Map<String, Object> analise = new HashMap<>();
        analise.put("mes", mes);
        analise.put("ano", ano);
        analise.put("custoFixoPrevisto", despesasFixas);
        analise.put("custoVariavel", despesasVariaveis);
        analise.put("investimentoEstoque", compraMercadorias);
        analise.put("totalSaidasMes", despesasFixas.add(despesasVariaveis).add(compraMercadorias));

        return analise;
    }

    // --- ESCRITA E BAIXAS ---

    @Transactional
    public ContaPagarDTO criar(ContaPagarDTO.NovaContaDTO dto) {
        ContaPagar conta = new ContaPagar();
        conta.setDescricao(dto.descricao());
        conta.setValorTotal(dto.valorOriginal());
        conta.setValorPago(BigDecimal.ZERO);
        conta.setDataVencimento(dto.dataVencimento());
        conta.setDataEmissao(dto.dataEmissao() != null ? dto.dataEmissao() : LocalDate.now());
        conta.setStatus(StatusConta.PENDENTE);

        if (dto.fornecedorId() != null) {
            Fornecedor f = fornecedorRepository.findById(dto.fornecedorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado"));
            conta.setFornecedor(f);
        }

        repository.save(conta);
        return converterParaDTO(conta);
    }

    @Transactional
    public void pagarConta(Long id, ContaPagarDTO.BaixaContaPagarDTO dto) {
        ContaPagar conta = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conta não encontrada: " + id));

        if (conta.getStatus() == StatusConta.PAGO) throw new ValidationException("Esta conta já está paga.");

        BigDecimal valorSaida = dto.valorPago() != null ? dto.valorPago() : BigDecimal.ZERO;
        BigDecimal valorPagoAtual = conta.getValorPago() != null ? conta.getValorPago() : BigDecimal.ZERO;
        BigDecimal valorTotal = conta.getValorTotal() != null ? conta.getValorTotal() : BigDecimal.ZERO;

        BigDecimal novoTotalPago = valorPagoAtual.add(valorSaida);
        conta.setValorPago(novoTotalPago);

        if (novoTotalPago.compareTo(valorTotal) >= 0) {
            conta.setStatus(StatusConta.PAGO);
            conta.setDataPagamento(dto.dataPagamento() != null ? dto.dataPagamento() : LocalDate.now());
        } else {
            conta.setStatus(StatusConta.PARCIAL);
        }

        repository.save(conta);

        try {
            CaixaDiario caixaAtual = caixaService.buscarCaixaAberto();
            if (caixaAtual != null) {
                MovimentacaoCaixa mov = new MovimentacaoCaixa();
                mov.setCaixa(caixaAtual);
                mov.setTipo(TipoMovimentacaoCaixa.SAIDA);
                mov.setValor(valorSaida);
                mov.setFormaPagamento(dto.formaPagamento());
                mov.setMotivo("Pagamento Despesa: " + conta.getDescricao());
                mov.setDataHora(LocalDateTime.now());
                caixaService.salvarMovimentacao(mov);
            }
        } catch (Exception e) {
            log.warn("Aviso: Não foi possível registrar saída no caixa: {}", e.getMessage());
        }
    }

    private BigDecimal calcularRestanteSeguro(ContaPagar c) {
        BigDecimal total = c.getValorTotal() == null ? BigDecimal.ZERO : c.getValorTotal();
        BigDecimal pago = c.getValorPago() == null ? BigDecimal.ZERO : c.getValorPago();
        return total.subtract(pago);
    }

    private ContaPagarDTO converterParaDTO(ContaPagar c) {
        return new ContaPagarDTO(
                c.getId(), c.getDescricao(),
                c.getFornecedor() != null ? c.getFornecedor().getId() : null,
                c.getFornecedor() != null ? c.getFornecedor().getRazaoSocial() : "Despesa Avulsa",
                c.getValorTotal(), c.getValorPago(), calcularRestanteSeguro(c),
                c.getDataVencimento(), c.getDataEmissao(), c.getDataPagamento(), c.getStatus()
        );
    }

    private boolean filtrarPorStatus(ContaPagar c, String status) {
        if (status == null || status.equalsIgnoreCase("TODAS")) return true;
        if (c.getStatus() == null) return false;
        return c.getStatus().name().equalsIgnoreCase(status);
    }

    private boolean filtrarPorTermo(ContaPagar c, String termo) {
        if (termo == null || termo.trim().isEmpty()) return true;
        String t = termo.toLowerCase();
        boolean descMatch = c.getDescricao() != null && c.getDescricao().toLowerCase().contains(t);
        boolean fornMatch = c.getFornecedor() != null && c.getFornecedor().getRazaoSocial() != null && c.getFornecedor().getRazaoSocial().toLowerCase().contains(t);
        return descMatch || fornMatch;
    }
}