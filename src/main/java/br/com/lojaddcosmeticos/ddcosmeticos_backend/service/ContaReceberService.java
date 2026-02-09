package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.financeiro.ContaReceberDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusConta;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ContaReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ContaReceberRepository;
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
public class ContaReceberService {

    @Autowired
    private ContaReceberRepository repository;

    @Autowired
    private CaixaService caixaService;

    /**
     * Lista contas a receber com filtros de Status e Termo de busca (Nome/ID).
     */
    @Transactional(readOnly = true)
    public List<ContaReceberDTO> listar(String statusStr, String termo) {
        List<ContaReceber> lista = repository.findAll();

        return lista.stream()
                .filter(c -> filtrarPorStatus(c, statusStr))
                .filter(c -> filtrarPorTermo(c, termo))
                .sorted(Comparator.comparing(ContaReceber::getDataVencimento))
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Calcula os totais (A Receber, Vencido, Recebido Hoje) para os cards do Dashboard.
     */
    @Transactional(readOnly = true)
    public ContaReceberDTO.ResumoContasDTO obterResumo() {
        List<ContaReceber> todas = repository.findAll();
        LocalDate hoje = LocalDate.now();

        // Total a Receber (Status diferente de PAGA)
        BigDecimal aReceber = todas.stream()
                .filter(c -> c.getStatus() != StatusConta.PAGO)
                .map(this::calcularRestanteSeguro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total Vencido (Não paga e Data < Hoje)
        BigDecimal vencido = todas.stream()
                .filter(c -> c.getStatus() != StatusConta.PAGO
                        && c.getDataVencimento() != null
                        && c.getDataVencimento().isBefore(hoje))
                .map(this::calcularRestanteSeguro)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Recebido Hoje (Data Pagamento == Hoje)
        BigDecimal recebidoHoje = todas.stream()
                .filter(c -> c.getDataPagamento() != null && c.getDataPagamento().isEqual(hoje))
                .map(c -> c.getValorPago() != null ? c.getValorPago() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ContaReceberDTO.ResumoContasDTO(aReceber, vencido, recebidoHoje);
    }

    /**
     * Realiza a baixa (pagamento) de um título.
     * Atualiza o status da conta e lança uma ENTRADA no caixa aberto.
     */
    @Transactional
    public void baixarTitulo(Long id, ContaReceberDTO.BaixaTituloDTO dto) {
        ContaReceber conta = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conta a receber não encontrada: " + id));

        if (conta.getStatus() == StatusConta.PAGO) {
            throw new ValidationException("Esta conta já está totalmente paga.");
        }

        BigDecimal valorRecebido = dto.valorPago() != null ? dto.valorPago() : BigDecimal.ZERO;
        BigDecimal valorJuros = dto.juros() != null ? dto.juros() : BigDecimal.ZERO;
        BigDecimal valorDesconto = dto.desconto() != null ? dto.desconto() : BigDecimal.ZERO;

        // Recupera valores atuais com segurança contra nulos
        BigDecimal totalPagoAnteriormente = conta.getValorPago() != null ? conta.getValorPago() : BigDecimal.ZERO;
        BigDecimal valorTotalOriginal = conta.getValorTotal() != null ? conta.getValorTotal() : BigDecimal.ZERO;

        // O valor real abatido da dívida pode ser ajustado por juros/descontos
        // Lógica simples: Consideramos o valor pago efetivo + desconto - juros como abatimento do principal?
        // OU Lógica direta: O que entrou no caixa é o valorRecebido. Somamos ao pago.
        // Vamos usar a lógica direta: Soma o que foi pago.
        BigDecimal novoTotalPago = totalPagoAnteriormente.add(valorRecebido);

        conta.setValorPago(novoTotalPago);

        // Verifica se quitou
        // Se (Pago >= Total) -> PAGA
        if (novoTotalPago.compareTo(valorTotalOriginal) >= 0) {
            conta.setStatus(StatusConta.PAGO);
            conta.setDataPagamento(dto.dataPagamento() != null ? dto.dataPagamento() : LocalDate.now());
        } else {
            conta.setStatus(StatusConta.PARCIAL);
        }

        repository.save(conta);

        // INTEGRACAO COM CAIXA (LANÇAR ENTRADA)
        try {
            CaixaDiario caixaAtual = caixaService.buscarCaixaAberto();
            if (caixaAtual != null) {
                MovimentacaoCaixa mov = new MovimentacaoCaixa();
                mov.setCaixa(caixaAtual);
                mov.setTipo(TipoMovimentacaoCaixa.ENTRADA); // É dinheiro entrando
                mov.setValor(valorRecebido);
                mov.setFormaPagamento(dto.formaPagamento());

                String nomeCliente = (conta.getCliente() != null) ? conta.getCliente().getNome() : "Cliente";
                String refVenda = (conta.getVenda() != null) ? "Ref. Venda #" + conta.getVenda().getIdVenda() : "Avulso";

                // Usa setMotivo conforme sua entidade MovimentacaoCaixa atual
                mov.setMotivo("Recebimento Crediário: " + nomeCliente + " (" + refVenda + ")");
                mov.setDataHora(LocalDateTime.now());

                caixaService.salvarMovimentacao(mov);
            }
        } catch (Exception e) {
            System.err.println("Aviso: Falha ao integrar com caixa: " + e.getMessage());
            // Não lançamos erro aqui para não desfazer o recebimento da conta caso o caixa esteja fechado/erro
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private BigDecimal calcularRestanteSeguro(ContaReceber c) {
        BigDecimal total = c.getValorTotal() == null ? BigDecimal.ZERO : c.getValorTotal();
        BigDecimal pago = c.getValorPago() == null ? BigDecimal.ZERO : c.getValorPago();
        return total.subtract(pago);
    }

    private ContaReceberDTO converterParaDTO(ContaReceber c) {
        BigDecimal restante = calcularRestanteSeguro(c);

        return new ContaReceberDTO(
                c.getId(),
                c.getVenda() != null ? c.getVenda().getIdVenda() : null,
                c.getCliente() != null ? c.getCliente().getNome() : "Consumidor Final",
                c.getCliente() != null ? c.getCliente().getTelefone() : "",
                c.getValorTotal(),
                restante,
                c.getValorPago(),
                c.getDataVencimento(),
                c.getDataEmissao(),
                c.getDataPagamento(),
                c.getStatus()
        );
    }

    private boolean filtrarPorStatus(ContaReceber c, String status) {
        if (status == null || status.equalsIgnoreCase("TODAS")) return true;
        if (c.getStatus() == null) return false;
        return c.getStatus().name().equalsIgnoreCase(status);
    }

    private boolean filtrarPorTermo(ContaReceber c, String termo) {
        if (termo == null || termo.trim().isEmpty()) return true;
        String t = termo.toLowerCase();

        boolean nomeMatch = c.getCliente() != null
                && c.getCliente().getNome() != null
                && c.getCliente().getNome().toLowerCase().contains(t);

        boolean idMatch = c.getId().toString().equals(t);

        return nomeMatch || idMatch;
    }
}