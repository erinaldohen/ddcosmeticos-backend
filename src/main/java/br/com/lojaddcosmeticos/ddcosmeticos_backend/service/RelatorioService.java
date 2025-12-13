package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ItemVenda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ItemVendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RelatorioService {

    @Autowired
    private VendaRepository vendaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ItemVendaRepository itemVendaRepository;

    @Autowired private AuditoriaRepository auditoriaRepository;

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

    /**
     * Gera o Inventário baseado no tipo solicitado.
     * @param apenasFiscal Se true, traz apenas produtos com NF de entrada.
     */
    public InventarioResponseDTO gerarInventarioEstoque(boolean apenasFiscal) {
        List<Produto> produtos;

        if (apenasFiscal) {
            // Visão do Contador (Apenas o que existe "oficialmente")
            produtos = produtoRepository.findByAtivoTrueAndPossuiNfEntradaTrue();
        } else {
            // Visão do Gerente (Tudo o que está na prateleira)
            produtos = produtoRepository.findAllByAtivoTrue();
        }

        List<ItemInventarioDTO> itensDTO = new ArrayList<>();
        BigDecimal valorTotalGeral = BigDecimal.ZERO;

        for (Produto p : produtos) {
            // Calcula valor total deste item (Qtd * PMP)
            BigDecimal custo = p.getPrecoMedioPonderado() != null ? p.getPrecoMedioPonderado() : BigDecimal.ZERO;
            BigDecimal qtd = p.getQuantidadeEmEstoque();
            BigDecimal totalItem = custo.multiply(qtd);

            ItemInventarioDTO item = ItemInventarioDTO.builder()
                    .codigoBarras(p.getCodigoBarras())
                    .descricao(p.getDescricao())
                    .unidade("UN") // Pode vir do cadastro se tiver campo Unidade
                    .quantidade(qtd)
                    .custoUnitarioPmp(custo)
                    .valorTotalEstoque(totalItem)
                    .statusFiscal(p.isPossuiNfEntrada() ? "FISCAL" : "NAO_FISCAL")
                    .build();

            itensDTO.add(item);
            valorTotalGeral = valorTotalGeral.add(totalItem);
        }

        return InventarioResponseDTO.builder()
                .tipoInventario(apenasFiscal ? "CONTABIL_FISCAL (Apenas com NF)" : "GERENCIAL_COMPLETO (Físico Real)")
                .dataGeracao(LocalDateTime.now())
                .totalItens(itensDTO.size())
                .valorTotalEstoque(valorTotalGeral)
                .itens(itensDTO)
                .build();
    }

    public List<RelatorioPerdasDTO> gerarRelatorioPerdasPorMotivo() {
        // 1. Busca todas as auditorias de perda
        List<Auditoria> perdas = auditoriaRepository.findAll().stream()
                .filter(a -> a.getTipoEvento().equals("INVENTARIO_PERDA"))
                .toList();

        Map<String, BigDecimal> mapaPrejuizo = new HashMap<>();
        Map<String, Long> mapaContagem = new HashMap<>();

        for (Auditoria a : perdas) {
            // Extrai o motivo da mensagem (fizemos um padrão: "[MOTIVO: XXX] ...")
            String msg = a.getMensagem();
            String motivo = "OUTROS";

            if (msg.contains("[MOTIVO:") && msg.contains("]")) {
                motivo = msg.substring(msg.indexOf(":") + 1, msg.indexOf("]")).trim();
            }

            // Para calcular o valor exato, precisaríamos cruzar com o MovimentoEstoque pelo ID/Data.
            // Por simplificação, vamos contar as ocorrências aqui.
            // *Em uma evolução, faremos um JOIN no banco.*

            // Acumula contagem
            mapaContagem.put(motivo, mapaContagem.getOrDefault(motivo, 0L) + 1);

            // Simulação de valor (para o exemplo funcionar sem query complexa)
            // Em produção, você pegaria o valor do movimento_estoque associado
            mapaPrejuizo.put(motivo, mapaPrejuizo.getOrDefault(motivo, BigDecimal.ZERO).add(BigDecimal.TEN));
        }

        // Transforma em Lista DTO
        List<RelatorioPerdasDTO> resultado = new ArrayList<>();
        for (String motivo : mapaContagem.keySet()) {
            resultado.add(new RelatorioPerdasDTO(
                    motivo,
                    mapaContagem.get(motivo),
                    mapaPrejuizo.get(motivo)
            ));
        }

        return resultado;
    }

}