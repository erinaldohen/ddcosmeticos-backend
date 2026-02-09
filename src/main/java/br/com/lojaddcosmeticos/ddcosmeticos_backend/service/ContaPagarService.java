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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContaPagarService {

    @Autowired
    private ContaPagarRepository repository;
    @Autowired
    private FornecedorRepository fornecedorRepository;
    @Autowired
    private CaixaService caixaService;

    @Transactional(readOnly = true)
    public List<ContaPagarDTO> listar(String statusStr, String termo) {
        List<ContaPagar> lista = repository.findAll();

        return lista.stream()
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
                .filter(c -> c.getStatus() != StatusConta.PAGO
                        && c.getDataVencimento() != null
                        && c.getDataVencimento().isBefore(hoje))
                .map(this::calcularRestanteSeguro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pagoHoje = todas.stream()
                .filter(c -> c.getDataPagamento() != null && c.getDataPagamento().isEqual(hoje))
                .map(c -> c.getValorPago() != null ? c.getValorPago() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ContaPagarDTO.ResumoPagarDTO(aPagar, vencido, pagoHoje);
    }

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
        ContaPagar conta = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a pagar não encontrada: " + id));

        if (conta.getStatus() == StatusConta.PAGO) {
            throw new ValidationException("Esta conta já está paga.");
        }

        BigDecimal valorSaida = dto.valorPago() != null ? dto.valorPago() : BigDecimal.ZERO;
        BigDecimal valorPagoAtual = conta.getValorPago() != null ? conta.getValorPago() : BigDecimal.ZERO;
        BigDecimal valorTotal = conta.getValorTotal() != null ? conta.getValorTotal() : BigDecimal.ZERO;

        // Atualiza conta
        BigDecimal novoTotalPago = valorPagoAtual.add(valorSaida);
        conta.setValorPago(novoTotalPago);

        if (novoTotalPago.compareTo(valorTotal) >= 0) {
            conta.setStatus(StatusConta.PAGO);
            conta.setDataPagamento(dto.dataPagamento() != null ? dto.dataPagamento() : LocalDate.now());
        } else {
            conta.setStatus(StatusConta.PARCIAL);
        }

        repository.save(conta);

        // Lança SAÍDA no Caixa
        try {
            CaixaDiario caixaAtual = caixaService.buscarCaixaAberto();
            if (caixaAtual != null) {
                MovimentacaoCaixa mov = new MovimentacaoCaixa();
                mov.setCaixa(caixaAtual);
                mov.setTipo(TipoMovimentacaoCaixa.SAIDA);
                mov.setValor(valorSaida);
                mov.setFormaPagamento(dto.formaPagamento());
                mov.setMotivo("Pagamento Despesa: " + conta.getDescricao()); // Usa setMotivo conforme sua entidade
                mov.setDataHora(LocalDateTime.now());

                caixaService.salvarMovimentacao(mov);
            }
        } catch (Exception e) {
            System.err.println("Aviso: Não foi possível registrar saída no caixa: " + e.getMessage());
        }
    }

    private BigDecimal calcularRestanteSeguro(ContaPagar c) {
        BigDecimal total = c.getValorTotal() == null ? BigDecimal.ZERO : c.getValorTotal();
        BigDecimal pago = c.getValorPago() == null ? BigDecimal.ZERO : c.getValorPago();
        return total.subtract(pago);
    }

    private ContaPagarDTO converterParaDTO(ContaPagar c) {
        BigDecimal restante = calcularRestanteSeguro(c);
        return new ContaPagarDTO(
                c.getId(),
                c.getDescricao(),
                c.getFornecedor() != null ? c.getFornecedor().getId() : null,
                c.getFornecedor() != null ? c.getFornecedor().getRazaoSocial() : "Despesa Avulsa",
                c.getValorTotal(),
                c.getValorPago(),
                restante,
                c.getDataVencimento(),
                c.getDataEmissao(),
                c.getDataPagamento(),
                c.getStatus()
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
        boolean fornMatch = c.getFornecedor() != null
                && c.getFornecedor().getRazaoSocial() != null
                && c.getFornecedor().getRazaoSocial().toLowerCase().contains(t);

        return descMatch || fornMatch;
    }
}