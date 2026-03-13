package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RegistrarInteracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Cliente;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.InteracaoCRM;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Venda;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ClienteRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.InteracaoCRMRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CRMService {

    private final VendaRepository vendaRepository;
    private final InteracaoCRMRepository interacaoRepository;
    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;

    // =========================================================================
    // 1. REGISTAR NOVA INTERAÇÃO (Botão WhatsApp Clicado)
    // =========================================================================
    @Transactional
    public void registrarInteracao(RegistrarInteracaoDTO dto) {
        Usuario vendedor = capturarUsuarioLogado();
        if (vendedor == null) {
            throw new RuntimeException("Sessão inválida. Não foi possível identificar o vendedor.");
        }

        Cliente cliente = clienteRepository.findById(dto.clienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado com ID: " + dto.clienteId()));

        InteracaoCRM interacao = new InteracaoCRM();
        interacao.setCliente(cliente);
        interacao.setVendedor(vendedor);
        interacao.setDataContato(LocalDateTime.now());
        interacao.setTipoAbordagem(dto.tipoAbordagem());
        interacao.setResultado(dto.resultado());
        interacao.setObservacao(dto.observacao());

        interacaoRepository.save(interacao);
        log.info("Interação do tipo {} registrada para o cliente {} pelo vendedor {}", dto.tipoAbordagem(), cliente.getNome(), vendedor.getNome());
    }

    // =========================================================================
    // 2. BUSCAR DADOS DO CRM (Filtrando os já contactados)
    // =========================================================================
    @Transactional(readOnly = true)
    public Map<String, Object> obterDadosCRM() {
        Map<String, Object> response = new HashMap<>();
        LocalDateTime agora = LocalDateTime.now();
        DateTimeFormatter fmtData = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // 1. Busca as vendas
        List<Venda> vendasIdentificadas = vendaRepository.buscarTodasVendasIdentificadas();

        // 2. AGRUPAMENTO BLINDADO
        Map<String, List<Venda>> historicoPorCliente = vendasIdentificadas.stream()
                .filter(v -> (v.getClienteTelefone() != null && !v.getClienteTelefone().trim().isEmpty()) ||
                        (v.getClienteDocumento() != null && !v.getClienteDocumento().trim().isEmpty()))
                .collect(Collectors.groupingBy(v -> {
                    if (v.getClienteTelefone() != null && !v.getClienteTelefone().trim().isEmpty()) {
                        return v.getClienteTelefone().trim();
                    }
                    return v.getClienteDocumento().trim();
                }));

        int clientesAtivos = 0;
        int clientesEmRisco = 0;
        int recuperadosMes = 0;
        BigDecimal faturamentoTotalCRM = BigDecimal.ZERO;
        int totalTicketsCRM = vendasIdentificadas.size();

        List<Map<String, Object>> clientesBase = new ArrayList<>();
        List<Map<String, Object>> tarefas = new ArrayList<>();
        int idTarefaCount = 1;

        // 3. Analisa o comportamento de forma segura
        for (Map.Entry<String, List<Venda>> entry : historicoPorCliente.entrySet()) {
            List<Venda> historico = entry.getValue();

            // Ordenação protegida contra data nula
            historico.sort((v1, v2) -> {
                LocalDateTime d1 = v1.getDataVenda() != null ? v1.getDataVenda() : LocalDateTime.MIN;
                LocalDateTime d2 = v2.getDataVenda() != null ? v2.getDataVenda() : LocalDateTime.MIN;
                return d2.compareTo(d1);
            });

            Venda ultimaVenda = historico.get(0);
            LocalDateTime dataRef = ultimaVenda.getDataVenda() != null ? ultimaVenda.getDataVenda() : agora;
            long diasDesdeUltimaCompra = ChronoUnit.DAYS.between(dataRef, agora);

            String nomeCliente = ultimaVenda.getClienteNome() != null && !ultimaVenda.getClienteNome().trim().isEmpty()
                    ? ultimaVenda.getClienteNome() : "Cliente VIP";

            // Pega o ID do cliente se ele for um cliente registado no banco
            Long idClienteReal = ultimaVenda.getCliente() != null ? ultimaVenda.getCliente().getId() : null;

            BigDecimal totalGastoCliente = historico.stream()
                    .map(v -> v.getValorTotal() != null ? v.getValorTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            faturamentoTotalCRM = faturamentoTotalCRM.add(totalGastoCliente);

            String status = "ATIVO";
            if (diasDesdeUltimaCompra <= 90) {
                clientesAtivos++;
                if (historico.size() > 1 && ultimaVenda.getDataVenda() != null && ultimaVenda.getDataVenda().getMonth() == agora.getMonth()) {
                    Venda penultimaVenda = historico.get(1);
                    LocalDateTime dataPenultima = penultimaVenda.getDataVenda() != null ? penultimaVenda.getDataVenda() : agora;
                    long gapEntreVendas = ChronoUnit.DAYS.between(dataPenultima, dataRef);
                    if (gapEntreVendas > 90) recuperadosMes++;
                }
            } else if (diasDesdeUltimaCompra <= 180) {
                status = "EM_RISCO";
                clientesEmRisco++;
            } else {
                status = "INATIVO";
            }

            // Descobre o Produto mais comprado
            Map<String, Long> contagemProdutos = new HashMap<>();
            historico.forEach(v -> {
                if (v.getItens() != null) {
                    v.getItens().forEach(i -> {
                        String desc = (i.getProduto() != null && i.getProduto().getDescricao() != null) ? i.getProduto().getDescricao() : "Cosméticos";
                        contagemProdutos.put(desc, contagemProdutos.getOrDefault(desc, 0L) + 1);
                    });
                }
            });

            String produtoFavorito = contagemProdutos.isEmpty() ? "Cosméticos Diversos" :
                    contagemProdutos.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("Cosméticos Diversos");

            List<String> tags = new ArrayList<>();
            if (historico.size() > 3) tags.add("Cliente Fiel");
            if (totalGastoCliente.compareTo(new BigDecimal("1000")) > 0) tags.add("Ticket Alto");
            tags.add("Compra: " + (produtoFavorito.length() > 15 ? produtoFavorito.substring(0, 15) + "..." : produtoFavorito));

            // Salva na Base de forma nativa
            Map<String, Object> mapCliente = new HashMap<>();
            mapCliente.put("id", idClienteReal != null ? idClienteReal : entry.getKey().hashCode()); // Usa o ID real ou um hash para a tabela visual
            mapCliente.put("nome", nomeCliente);
            mapCliente.put("telefone", ultimaVenda.getClienteTelefone() != null && !ultimaVenda.getClienteTelefone().trim().isEmpty() ? ultimaVenda.getClienteTelefone() : ultimaVenda.getClienteDocumento());
            mapCliente.put("status", status);
            mapCliente.put("ultimaCompra", dataRef.format(fmtData));
            mapCliente.put("totalGasto", totalGastoCliente);
            mapCliente.put("tags", tags);
            clientesBase.add(mapCliente);

            // ==========================================================
            // GERAÇÃO DE TAREFAS (COM FILTRO ANTI-SPAM)
            // ==========================================================
            if (ultimaVenda.getClienteTelefone() != null && !ultimaVenda.getClienteTelefone().trim().isEmpty() && idClienteReal != null && tarefas.size() < 15) {

                // VERIFICA SE O CLIENTE JÁ FOI CONTACTADO NOS ÚLTIMOS 15 DIAS
                LocalDateTime quinzeDiasAtras = agora.minusDays(15);
                boolean jaContactadoRecentemente = interacaoRepository.buscarContatoRecente(idClienteReal, quinzeDiasAtras).isPresent();

                if (!jaContactadoRecentemente) {
                    Map<String, Object> tarefa = new HashMap<>();
                    boolean gerarTarefa = false;

                    if (diasDesdeUltimaCompra >= 40 && diasDesdeUltimaCompra <= 60) {
                        tarefa.put("tipo", "REPOSICAO");
                        tarefa.put("produtoFoco", produtoFavorito);
                        tarefa.put("mensagemSugerida", "Oi " + nomeCliente.split(" ")[0] + ", tudo bem? Vi aqui que seu " + produtoFavorito + " já deve estar no finalzinho. Quer que eu separe um novo para você?");
                        gerarTarefa = true;
                    } else if (diasDesdeUltimaCompra >= 90 && diasDesdeUltimaCompra <= 120) {
                        tarefa.put("tipo", "CHURN");
                        tarefa.put("produtoFoco", "Voucher de Retorno");
                        tarefa.put("mensagemSugerida", "Oi " + nomeCliente.split(" ")[0] + ", sentimos a sua falta por aqui! Liberei um desconto especial para você usar essa semana.");
                        gerarTarefa = true;
                    } else if (diasDesdeUltimaCompra >= 10 && diasDesdeUltimaCompra <= 25 && historico.size() > 1) {
                        tarefa.put("tipo", "UPSELL");
                        tarefa.put("produtoFoco", "Novidades do setor");
                        tarefa.put("mensagemSugerida", nomeCliente.split(" ")[0] + ", chegaram novidades incríveis na DD Cosméticos. Posso te mandar as fotos?");
                        gerarTarefa = true;
                    }

                    if (gerarTarefa) {
                        tarefa.put("id", idTarefaCount++);
                        tarefa.put("clienteId", idClienteReal); // Enviamos o ID Real para o React saber de quem é a tarefa
                        tarefa.put("clienteNome", nomeCliente);
                        tarefa.put("telefone", ultimaVenda.getClienteTelefone());
                        tarefa.put("diasUltimaCompra", diasDesdeUltimaCompra);
                        tarefas.add(tarefa);
                    }
                }
            }
        }

        BigDecimal ticketMedioCRM = totalTicketsCRM > 0
                ? faturamentoTotalCRM.divide(new BigDecimal(totalTicketsCRM), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> resumo = new HashMap<>();
        resumo.put("clientesAtivos", clientesAtivos);
        resumo.put("clientesEmRisco", clientesEmRisco);
        resumo.put("recuperadosMes", recuperadosMes);
        resumo.put("ticketMedioCRM", ticketMedioCRM);

        response.put("resumo", resumo);
        response.put("tarefas", tarefas);
        response.put("clientes", clientesBase);

        return response;
    }

    // Auxiliar para pegar o vendedor da sessão
    private Usuario capturarUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) return null;
        return usuarioRepository.findByMatriculaOrEmail(auth.getName(), auth.getName()).orElse(null);
    }
}