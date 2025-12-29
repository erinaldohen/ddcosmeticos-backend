package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RelatorioService {

    // ==================================================================================
    // SESSÃO 1: DEPENDÊNCIAS
    // ==================================================================================
    @Autowired private VendaRepository vendaRepository;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private ItemVendaRepository itemVendaRepository;
    @Autowired private ContaReceberRepository contaReceberRepository; // Novo para o Fiado
    @Autowired private ClienteRepository clienteRepository; // Novo para o Fiado
    @Autowired private AuditoriaRepository auditoriaRepository; // Mantido para compatibilidade futura

    // ==================================================================================
    // SESSÃO 2: RELATÓRIOS FISCAIS E DE ESTOQUE
    // ==================================================================================

    /**
     * Relatório de produtos monofásicos (Isenção de PIS/COFINS).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> gerarRelatorioMonofasicos() {
        return produtoRepository.findAllByAtivoTrue().stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("codigo", p.getCodigoBarras());
            map.put("produto", p.getDescricao());
            map.put("ncm", p.getNcm());
            map.put("status", p.isMonofasico() ? "MONOFÁSICO (ISENTO)" : "TRIBUTADO NORMAL");
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Inventário de Estoque para contabilidade ou conferência física.
     */
    @Transactional(readOnly = true)
    public InventarioResponseDTO gerarInventarioEstoque(boolean contabil) {
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue();

        if (contabil) {
            produtos = produtos.stream().filter(Produto::isPossuiNfEntrada).collect(Collectors.toList());
        }

        List<ItemInventarioDTO> itensDTO = produtos.stream()
                .map(p -> {
                    BigDecimal quantidade = p.getQuantidadeEmEstoque() != null
                            ? new BigDecimal(p.getQuantidadeEmEstoque())
                            : BigDecimal.ZERO;

                    BigDecimal custoMedio = p.getPrecoMedioPonderado() != null
                            ? p.getPrecoMedioPonderado()
                            : BigDecimal.ZERO;

                    BigDecimal total = quantidade.multiply(custoMedio);

                    return ItemInventarioDTO.builder()
                            .codigoBarras(p.getCodigoBarras())
                            .descricao(p.getDescricao())
                            .unidade(p.getUnidade())
                            .quantidade(quantidade)
                            .custoUnitarioPmp(custoMedio)
                            .valorTotalEstoque(total)
                            .statusFiscal(p.isPossuiNfEntrada() ? "FISCAL" : "GERENCIAL")
                            .build();
                })
                .collect(Collectors.toList());

        BigDecimal totalFinanceiro = itensDTO.stream()
                .map(ItemInventarioDTO::valorTotalEstoque)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return InventarioResponseDTO.builder()
                .tipoInventario(contabil ? "CONTABIL" : "FISICO")
                .dataGeracao(LocalDateTime.now())
                .totalItens(itensDTO.size())
                .valorTotalEstoque(totalFinanceiro)
                .itens(itensDTO)
                .build();
    }

    // ==================================================================================
    // SESSÃO 3: RELATÓRIOS DE PERFORMANCE (VENDAS E CURVA ABC)
    // ==================================================================================

    /**
     * Curva ABC para identificação de produtos estratégicos.
     */
    @Transactional(readOnly = true)
    public List<ItemAbcDTO> gerarCurvaAbc() {
        List<ItemAbcDTO> listaBruta = itemVendaRepository.agruparVendasPorProduto();
        BigDecimal faturamentoTotal = listaBruta.stream().map(ItemAbcDTO::valorTotalVendido).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (faturamentoTotal.compareTo(BigDecimal.ZERO) == 0) return new ArrayList<>();

        List<ItemAbcDTO> listaEnriquecida = new ArrayList<>();
        BigDecimal acumuladoValor = BigDecimal.ZERO;

        for (ItemAbcDTO item : listaBruta) {
            acumuladoValor = acumuladoValor.add(item.valorTotalVendido());
            double percAcumulada = acumuladoValor.divide(faturamentoTotal, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).doubleValue();

            String classe = percAcumulada <= 80.0 ? "A" : (percAcumulada <= 95.0 ? "B" : "C");

            listaEnriquecida.add(new ItemAbcDTO(item.codigoBarras(), item.nomeProduto(), item.quantidadeVendida(),
                    item.valorTotalVendido(), 0.0, percAcumulada, classe));
        }
        return listaEnriquecida;
    }

    @Transactional(readOnly = true)
    public RelatorioVendasDTO gerarRelatorioVendas(LocalDate inicio, LocalDate fim) {
        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(LocalTime.MAX);

        List<Venda> vendas = vendaRepository.buscarPorPeriodo(dataInicio, dataFim);

        BigDecimal totalDescontos = BigDecimal.ZERO;
        BigDecimal totalLiquido = BigDecimal.ZERO;
        BigDecimal custoTotalMercadoria = BigDecimal.ZERO;

        for (Venda venda : vendas) {
            totalLiquido = totalLiquido.add(venda.getTotalVenda());
            totalDescontos = totalDescontos.add(venda.getDescontoTotal() != null ? venda.getDescontoTotal() : BigDecimal.ZERO);

            for (ItemVenda item : venda.getItens()) {
                custoTotalMercadoria = custoTotalMercadoria.add(item.getCustoTotal() != null ? item.getCustoTotal() : BigDecimal.ZERO);
            }
        }

        BigDecimal totalBruto = totalLiquido.add(totalDescontos);
        BigDecimal lucroBruto = totalLiquido.subtract(custoTotalMercadoria);
        BigDecimal margem = totalLiquido.compareTo(BigDecimal.ZERO) > 0
                ? lucroBruto.divide(totalLiquido, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        Map<Integer, List<Venda>> agrupadoPorHora = vendas.stream()
                .collect(Collectors.groupingBy(v -> v.getDataVenda().getHour()));

        List<VendasPorHoraDTO> vendasPorHora = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            List<Venda> vh = agrupadoPorHora.getOrDefault(h, new ArrayList<>());
            BigDecimal totalH = vh.stream().map(Venda::getTotalVenda).reduce(BigDecimal.ZERO, BigDecimal::add);
            vendasPorHora.add(new VendasPorHoraDTO(h, totalH, (long) vh.size()));
        }

        return new RelatorioVendasDTO(
                inicio, fim, vendas.size(), totalBruto, totalDescontos,
                totalLiquido, custoTotalMercadoria, lucroBruto, margem,
                vendasPorHora
        );
    }

    // ==================================================================================
    // SESSÃO 4: RELATÓRIOS FINANCEIROS (NOVO - FIADO)
    // ==================================================================================

    @Transactional(readOnly = true)
    public List<RelatorioInadimplenciaDTO> gerarRelatorioFiado() {
        List<RelatorioInadimplenciaDTO> relatorio = new ArrayList<>();

        // 1. Busca quem deve (CPFs únicos)
        List<String> cpfsDevedores = contaReceberRepository.buscarCpfsComPendencia();

        for (String cpf : cpfsDevedores) {
            if (cpf == null) continue;

            // 2. Busca dados do cliente
            Optional<Cliente> clienteOpt = clienteRepository.findByCpf(cpf);
            String nome = clienteOpt.map(Cliente::getNome).orElse("Cliente Não Cadastrado");
            String telefone = clienteOpt.map(Cliente::getTelefone).orElse("-");

            // 3. Busca contas
            List<ContaReceber> contas = contaReceberRepository.listarContasEmAberto(cpf);

            BigDecimal totalDevido = BigDecimal.ZERO;
            List<TituloPendenteDTO> detalhes = new ArrayList<>();
            int contasAtrasadas = 0;

            for (ContaReceber conta : contas) {
                totalDevido = totalDevido.add(conta.getValorLiquido());

                long diasAtraso = ChronoUnit.DAYS.between(conta.getDataVencimento(), LocalDate.now());
                if (diasAtraso > 0) {
                    contasAtrasadas++;
                } else {
                    diasAtraso = 0;
                }

                detalhes.add(new TituloPendenteDTO(
                        conta.getId(),
                        conta.getIdVendaRef(),
                        conta.getDataVencimento(),
                        conta.getValorLiquido(),
                        diasAtraso
                ));
            }

            relatorio.add(new RelatorioInadimplenciaDTO(
                    nome,
                    cpf,
                    telefone,
                    totalDevido,
                    contasAtrasadas,
                    detalhes
            ));
        }
        return relatorio;
    }
}