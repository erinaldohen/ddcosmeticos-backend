package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.ProdutoRankingDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaDiariaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.relatorio.VendaPorPagamentoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    @Autowired private ContaReceberRepository contaReceberRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private AuditoriaRepository auditoriaRepository;

    // ==================================================================================
    // SESSÃO 2: RELATÓRIOS FISCAIS E DE ESTOQUE
    // ==================================================================================

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

    @Transactional(readOnly = true)
    public InventarioResponseDTO gerarInventarioEstoque(boolean contabil) {
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue();

        if (contabil) {
            produtos = produtos.stream().filter(Produto::isPossuiNfEntrada).collect(Collectors.toList());
        }

        List<ItemInventarioDTO> itensDTO = produtos.stream()
                .map(p -> {
                    BigDecimal quantidade = p.getQuantidadeEmEstoque() != null ? new BigDecimal(p.getQuantidadeEmEstoque()) : BigDecimal.ZERO;
                    BigDecimal custoMedio = p.getPrecoMedioPonderado() != null ? p.getPrecoMedioPonderado() : BigDecimal.ZERO;
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
    // SESSÃO 3: RELATÓRIOS DE PERFORMANCE
    // ==================================================================================

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
        // 1. Definição do Período
        if (inicio == null) inicio = LocalDate.now().withDayOfMonth(1);
        if (fim == null) fim = LocalDate.now();

        LocalDateTime dataInicio = inicio.atStartOfDay();
        LocalDateTime dataFim = fim.atTime(LocalTime.MAX);

        // 2. Buscas Analíticas
        List<VendaDiariaDTO> evolucao = vendaRepository.relatorioVendasPorDia(dataInicio, dataFim);
        List<VendaPorPagamentoDTO> pagamentos = vendaRepository.relatorioVendasPorPagamento(dataInicio, dataFim);
        List<ProdutoRankingDTO> topProdutos = vendaRepository.relatorioProdutosMaisVendidos(dataInicio, dataFim, PageRequest.of(0, 10));

        // 3. Cálculos de Totais
        BigDecimal faturamentoTotal = evolucao.stream().map(VendaDiariaDTO::totalVendido).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long totalVendas = evolucao.stream().mapToLong(VendaDiariaDTO::quantidadeVendas).sum();
        BigDecimal ticketMedio = (totalVendas > 0) ? faturamentoTotal.divide(BigDecimal.valueOf(totalVendas), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        // 4. Montagem do DTO
        return RelatorioVendasDTO.builder()
                .dataGeracao(LocalDateTime.now())
                .periodo(inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " a " + fim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .faturamentoTotal(faturamentoTotal)
                .totalVendasRealizadas(totalVendas)
                .ticketMedio(ticketMedio)
                .evolucaoDiaria(evolucao)
                .vendasPorPagamento(pagamentos)
                .produtosMaisVendidos(topProdutos)
                .build();
    }

    // ==================================================================================
    // SESSÃO 4: RELATÓRIOS FINANCEIROS (FIADO)
    // ==================================================================================

    @Transactional(readOnly = true)
    public List<RelatorioInadimplenciaDTO> gerarRelatorioFiado() {
        List<RelatorioInadimplenciaDTO> relatorio = new ArrayList<>();
        List<String> documentosDevedores = contaReceberRepository.buscarDocumentosComPendencia(); // Note: Método no repo ainda se chama 'buscarCpfs...' mas a query foi corrigida para usar 'clienteDocumento'

        for (String doc : documentosDevedores) {
            if (doc == null) continue;

            Optional<Cliente> clienteOpt = clienteRepository.findByDocumento(doc);
            String nome = clienteOpt.map(Cliente::getNome).orElse("Cliente Não Cadastrado");
            String telefone = clienteOpt.map(Cliente::getTelefone).orElse("-");

            List<ContaReceber> contas = contaReceberRepository.listarContasEmAberto(doc);
            BigDecimal totalDevido = BigDecimal.ZERO;
            List<TituloPendenteDTO> detalhes = new ArrayList<>();
            int contasAtrasadas = 0;

            for (ContaReceber conta : contas) {
                totalDevido = totalDevido.add(conta.getValorLiquido());
                long diasAtraso = ChronoUnit.DAYS.between(conta.getDataVencimento(), LocalDate.now());
                if (diasAtraso > 0) contasAtrasadas++;
                else diasAtraso = 0;

                detalhes.add(new TituloPendenteDTO(conta.getId(), conta.getIdVendaRef(), conta.getDataVencimento(), conta.getValorLiquido(), diasAtraso));
            }

            relatorio.add(new RelatorioInadimplenciaDTO(nome, doc, telefone, totalDevido, contasAtrasadas, detalhes));
        }
        return relatorio;
    }
}