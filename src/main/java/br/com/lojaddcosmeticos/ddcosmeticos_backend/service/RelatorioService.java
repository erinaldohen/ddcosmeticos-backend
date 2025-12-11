package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class RelatorioService {

    @Autowired
    private VendaRepository vendaRepository;

    public RelatorioDiarioDTO gerarRelatorioDoDia() {
        // Define o intervalo: Começo (00:00) até o Fim (23:59) do dia de hoje
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);

        // Busca as vendas
        List<Venda> vendasHoje = vendaRepository.findByDataVendaBetween(inicioDia, fimDia);

        // Inicializa acumuladores
        BigDecimal fatBruto = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;
        BigDecimal fatLiquido = BigDecimal.ZERO;
        BigDecimal cmvTotal = BigDecimal.ZERO;

        for (Venda venda : vendasHoje) {
            fatBruto = fatBruto.add(venda.getValorTotal()); // Valor de tabela
            descontos = descontos.add(venda.getDesconto());
            fatLiquido = fatLiquido.add(venda.getValorLiquido()); // O que o cliente pagou

            // Soma o Custo (PMP) de cada item vendido
            // Isso foi gravado no ItemVenda no momento exato da venda (snapshot)
            for (ItemVenda item : venda.getItens()) {
                if (item.getCustoTotal() != null) {
                    cmvTotal = cmvTotal.add(item.getCustoTotal());
                }
            }
        }

        // Cálculo do Lucro: (O que entrou) - (Quanto custou repor o estoque)
        BigDecimal lucro = fatLiquido.subtract(cmvTotal);

        // Cálculo da Margem: (Lucro / Faturamento Líquido) * 100
        Double margem = 0.0;
        if (fatLiquido.compareTo(BigDecimal.ZERO) > 0) {
            margem = lucro.divide(fatLiquido, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100)).doubleValue();
        }

        return RelatorioDiarioDTO.builder()
                .data(LocalDate.now())
                .quantidadeVendas(vendasHoje.size())
                .faturamentoBruto(fatBruto)
                .totalDescontos(descontos)
                .faturamentoLiquido(fatLiquido)
                .custoMercadoriaVendida(cmvTotal)
                .lucroLiquido(lucro)
                .margemLucroPorcentagem(margem)
                .build();
    }
}