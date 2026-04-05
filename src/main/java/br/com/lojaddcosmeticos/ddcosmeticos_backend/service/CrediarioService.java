package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.DevedorResumoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FaturaClienteDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RecebimentoRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.StatusTitulo;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.TituloReceber;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.TituloReceberRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CrediarioService {

    @Autowired
    private TituloReceberRepository tituloReceberRepository;
    @Autowired
    private VendaRepository vendaRepository;

    // TODO: @Autowired private CaixaDiarioService caixaService; (Para injetar o dinheiro recebido no caixa do dia)

    @Transactional(readOnly = true)
    public List<DevedorResumoDTO> listarResumoDevedores() {
        return tituloReceberRepository.findResumoDevedores();
    }

    @Transactional(readOnly = true)
    public List<FaturaClienteDTO> listarFaturasAbertasDoCliente(Long idCliente) {

        // 🚨 CORREÇÃO: Agora buscamos TODOS os títulos (Pendentes e Pagos)
        List<TituloReceber> titulos = tituloReceberRepository.findByClienteIdOrderByDataCompraDesc(idCliente);

        return titulos.stream().map(t -> {
            String statusAtual = t.getStatus().name();
            // Mantém a regra visual de atraso para os pendentes
            if (t.getStatus() == StatusTitulo.PENDENTE && t.getDataVencimento().isBefore(LocalDate.now())) {
                statusAtual = "ATRASADO";
            }

            long dias = java.time.temporal.ChronoUnit.DAYS.between(t.getDataCompra(), LocalDate.now());

            // Busca os itens da Venda
            List<FaturaClienteDTO.ItemFaturaDTO> itensDaVenda = new java.util.ArrayList<>();
            if (t.getVendaId() != null) {
                vendaRepository.findByIdComItens(t.getVendaId()).ifPresent(venda -> {
                    venda.getItens().forEach(item -> {
                        itensDaVenda.add(new FaturaClienteDTO.ItemFaturaDTO(
                                item.getDescricaoProduto(),
                                item.getQuantidade().intValue(),
                                item.getPrecoUnitario()
                        ));
                    });
                });
            }

            return new FaturaClienteDTO(
                    t.getId(), t.getDataCompra(), t.getDataVencimento(),
                    t.getDataPagamento(),
                    t.getValorTotal(), t.getSaldoDevedor(), statusAtual, t.getDescricao(),
                    dias, itensDaVenda
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public void processarRecebimento(Long idFatura, RecebimentoRequestDTO request) {
        TituloReceber titulo = tituloReceberRepository.findById(idFatura)
                .orElseThrow(() -> new br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException("Fatura não encontrada."));

        if (titulo.getStatus() == StatusTitulo.PAGO) {
            throw new br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException("Esta fatura já está paga.");
        }

        BigDecimal totalPagoNaOperacao = request.pagamentos().stream()
                .map(RecebimentoRequestDTO.PagamentoParcialDTO::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPagoNaOperacao.compareTo(BigDecimal.ZERO) <= 0) {
            throw new br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException("O valor pago deve ser maior que zero.");
        }

        if (totalPagoNaOperacao.compareTo(titulo.getSaldoDevedor()) > 0) {
            throw new br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException("O valor do pagamento não pode ser maior que o saldo devedor.");
        }

        // Dá baixa no Título
        titulo.setValorPago(titulo.getValorPago().add(totalPagoNaOperacao));
        titulo.setSaldoDevedor(titulo.getSaldoDevedor().subtract(totalPagoNaOperacao));
        titulo.setDataPagamento(LocalDate.now());

        if (titulo.getSaldoDevedor().compareTo(BigDecimal.ZERO) <= 0) {
            titulo.setStatus(StatusTitulo.PAGO);
        } else {
            titulo.setStatus(StatusTitulo.PENDENTE);
        }

        tituloReceberRepository.save(titulo);

        // TODO: Para injetar no caixa, você precisaria injetar o CaixaDiarioRepository,
        // buscar o caixa aberto do usuário logado (SecurityContextHolder) e somar os valores
        // exatamente como fizemos no VendaService (atualizarFinanceiro).
        // Exemplo: Para cada pagamento na lista, se for DINHEIRO, soma em caixa.saldoAtual e caixa.totalRecebimentoCrediario.
    }
}