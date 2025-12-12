package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ItemVendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RelatorioService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ItemVendaRepository itemVendaRepository;

    public RelatorioDiarioDTO gerarRelatorioDoDia() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        LocalDateTime fimDia = LocalDate.now().atTime(LocalTime.MAX);
        List<Venda> vendasHoje = vendaRepository.findByDataVendaBetween(inicioDia, fimDia);

        BigDecimal fatBruto = BigDecimal.ZERO;
        BigDecimal descontos = BigDecimal.ZERO;
        BigDecimal fatLiquido = BigDecimal.ZERO;
        BigDecimal cmvTotal = BigDecimal.ZERO;

        for (Venda venda : vendasHoje) {
            fatBruto = fatBruto.add(venda.getValorTotal());
            descontos = descontos.add(venda.getDesconto());
            fatLiquido = fatLiquido.add(venda.getValorLiquido());
            for (ItemVenda item : venda.getItens()) {
                if (item.getCustoTotal() != null) {
                    cmvTotal = cmvTotal.add(item.getCustoTotal());
                }
            }
        }
        BigDecimal lucro = fatLiquido.subtract(cmvTotal);
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

    /**
     * Gera a Curva ABC corrigida.
     * Lógica ajustada: O item é classificado baseado no acumulado ANTES de sua adição.
     */
    public List<ItemAbcDTO> gerarCurvaAbc() {
        List<ItemAbcDTO> lista = itemVendaRepository.agruparVendasPorProduto();

        if (lista.isEmpty()) return new ArrayList<>();

        BigDecimal faturamentoTotal = lista.stream()
                .map(ItemAbcDTO::getValorTotalVendido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (faturamentoTotal.compareTo(BigDecimal.ZERO) == 0) return lista;

        BigDecimal acumulado = BigDecimal.ZERO;

        for (ItemAbcDTO item : lista) {
            // Percentual deste item
            BigDecimal pctItem = item.getValorTotalVendido()
                    .divide(faturamentoTotal, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100));

            item.setPorcentagemDoFaturamento(pctItem.doubleValue());

            // CORREÇÃO: Verifica o acumulado ANTES de somar o atual
            // Se já tínhamos 0%, e agora vamos para 100%, o '0%' indica que ainda estamos começando a Classe A.
            double acumuladoAnterior = acumulado.doubleValue();

            acumulado = acumulado.add(pctItem);
            item.setAcumulado(acumulado.doubleValue());

            // Regra de Pareto Ajustada:
            // Se antes desse item nós tínhamos menos de 80%, ele entra na Classe A (mesmo que estoure os 80%).
            if (acumuladoAnterior < 80.0) {
                item.setClasse("A");
            } else if (acumuladoAnterior < 95.0) {
                item.setClasse("B");
            } else {
                item.setClasse("C");
            }
        }

        return lista;
    }
}