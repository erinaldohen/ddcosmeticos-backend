package br.com.lojaddcosmeticos.ddcosmeticos_backend.dto;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class VendaCompletaResponseDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // Dados Identificadores
    private Long id;
    private LocalDateTime dataVenda;
    private Usuario usuarioVendedor; // Auditoria de quem operou o caixa

    // Totais Financeiros
    private BigDecimal totalVenda;
    private BigDecimal descontoTotal;
    private BigDecimal faturamentoLiquido; // Total - Descontos

    // Dados do Cliente (Para NF-e/NFC-e)
    private String clienteCpf;
    private String clienteNome;

    // Status e Fiscal
    private String statusFiscal;
    private boolean cancelada;
    private String motivoCancelamento;
    private String formaPagamento;

    // Detalhamento dos Produtos (Usando o DTO que corrigimos na linha 37)
    private List<ItemVendaResponseDTO> itens;

    public VendaCompletaResponseDTO(Venda venda) {
        this.id = venda.getId();
        this.dataVenda = venda.getDataVenda();
        this.usuarioVendedor = venda.getUsuario();

        this.totalVenda = venda.getTotalVenda();
        this.descontoTotal = venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO;
        this.faturamentoLiquido = this.totalVenda.subtract(this.descontoTotal);

        this.clienteCpf = venda.getClienteCpf();
        this.clienteNome = venda.getClienteNome();

        this.statusFiscal = venda.getStatusFiscal().toString();
        this.cancelada = venda.isCancelada();
        this.motivoCancelamento = venda.getMotivoDoCancelamento();

        // Converte o Enum para String para exibição amigável no Frontend
        this.formaPagamento = venda.getFormaPagamento() != null ? venda.getFormaPagamento().name() : "N/I";

        // MAPEAMENTO DOS ITENS: Aqui usamos o construtor do ItemVendaResponseDTO que corrigimos
        if (venda.getItens() != null) {
            this.itens = venda.getItens().stream()
                    .map(ItemVendaResponseDTO::new)
                    .collect(Collectors.toList());
        }
    }

    public <R> VendaCompletaResponseDTO(Long id, LocalDateTime dataVenda, String clienteNome, BigDecimal totalVenda, BigDecimal descontoTotal, String name, R collect) {
    }

    // Método auxiliar para o Dashboard: Calcula o Lucro Total da Venda
    public BigDecimal getLucroBrutoVenda() {
        if (itens == null) return BigDecimal.ZERO;
        BigDecimal custoTotal = itens.stream()
                .map(ItemVendaResponseDTO::getCustoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return this.faturamentoLiquido.subtract(custoTotal);
    }
}