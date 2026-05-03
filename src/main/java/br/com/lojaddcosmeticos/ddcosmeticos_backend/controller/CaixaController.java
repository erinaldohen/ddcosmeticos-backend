package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.CaixaDiarioDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.FechamentoCaixaRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.MovimentacaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.CaixaDiario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentacaoCaixa;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.VendaPerdida;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.CaixaDiarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaPerdidaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.VendaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaRelatorioService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CaixaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/caixas")
@Tag(name = "Caixa / Operações Financeiras PDV", description = "Fechos de Caixa, Sangrias, Aberturas e Vendas Perdidas")
@RequiredArgsConstructor
public class CaixaController {

    private final CaixaService caixaService;
    private final CaixaDiarioRepository caixaRepository;
    private final CaixaRelatorioService relatorioService;
    private final VendaRepository vendaRepository;
    private final VendaPerdidaRepository vendaPerdidaRepository;

    @GetMapping("/alertas")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Alarme de Desvio Financeiro", description = "Alerta o painel quando há grandes quebras na contagem do Caixa Físico (Quebra Cega).")
    public ResponseEntity<List<CaixaDiarioDTO>> getAlertasDeRisco() {
        return ResponseEntity.ok(caixaService.buscarAlertasRiscoDashboard());
    }

    @GetMapping("/sugestao-ia/{produtoId}")
    @Operation(summary = "Cross-Sell Inteligente", description = "Lança sugestões para o operador do Caixa oferecer no balcão.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<String>> getSugestaoIA(@PathVariable Long produtoId) {
        try {
            return ResponseEntity.ok(vendaRepository.buscarSugestoesParaProduto(produtoId));
        } catch (Exception e) {
            log.warn("Erro ao gerar sugestão de IA para o produto {}: {}", produtoId, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/resumo-fechamento")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Buscar mapa do caixa antes do Fecho")
    public ResponseEntity<FechamentoCaixaDTO> obterResumoParaFechamento() {
        CaixaDiario caixaAberto = caixaService.buscarCaixaAberto();
        if (caixaAberto == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(caixaService.obterResumoFechamento(caixaAberto.getId()));
    }

    @PostMapping("/fechar")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fechar Turno/Caixa", description = "Consolida os pagamentos e verifica as quebras com base na contagem do operador.")
    public ResponseEntity<Void> fecharCaixa(@Valid @RequestBody FechamentoCaixaRequestDTO request) {
        caixaService.fecharCaixa(request.valorFisicoInformado(), request.justificativaDiferenca());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    @Operation(summary = "Verificar Operador Ativo", description = "Confirma se a sessão tem autorização (Caixa Aberto) para lançar vendas.")
    public ResponseEntity<Map<String, Object>> verificarStatusCaixa() {
        try {
            CaixaDiarioDTO caixa = caixaService.buscarStatusAtual();
            boolean isAberto = caixa != null && "ABERTO".equals(caixa.status());

            return ResponseEntity.ok(isAberto
                    ? Map.of("status", "ABERTO", "aberto", true, "caixa", caixa)
                    : Map.of("status", "FECHADO", "aberto", false));
        } catch (Exception e) {
            log.error("Erro crítico ao verificar status do caixa: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of("status", "FECHADO", "aberto", false));
        }
    }

    @PostMapping("/abrir")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Abrir Turno")
    public ResponseEntity<CaixaDiarioDTO> abrirCaixa(@RequestBody Map<String, BigDecimal> payload) {
        return ResponseEntity.ok(caixaService.abrirCaixa(payload.get("saldoInicial")));
    }

    @GetMapping("/motivos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Autocompletar motivos standard de sangria/suprimento")
    public ResponseEntity<List<String>> getMotivosFrequentes() {
        return ResponseEntity.ok(caixaService.listarMotivosFrequentes());
    }

    @PostMapping("/sangria")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Retirar Trocos/Numerário (Sangria)")
    public ResponseEntity<Void> realizarSangria(@RequestBody MovimentacaoDTO dto) {
        caixaService.realizarSangria(dto.getValor(), dto.getMotivo());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/suprimento")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Injetar Trocos/Numerário (Suprimento)")
    public ResponseEntity<Void> realizarSuprimento(@RequestBody MovimentacaoDTO dto) {
        caixaService.realizarSuprimento(dto.getValor(), dto.getMotivo());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @Operation(summary = "Histórico de Turnos Globais da Loja")
    public ResponseEntity<Page<CaixaDiarioDTO>> listarTodos(
            Pageable pageable,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return ResponseEntity.ok(caixaService.listarHistoricoPaginado(inicio, fim, pageable));
    }

    @GetMapping("/diario")
    @Operation(summary = "Ver detalhes do dia (Timeline de eventos de caixa)")
    public ResponseEntity<List<MovimentacaoCaixa>> getHistoricoDiario(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        LocalDate target = (data != null) ? data : LocalDate.now();
        return ResponseEntity.ok(caixaService.buscarHistorico(target, target));
    }

    @GetMapping("/relatorio/pdf")
    @Operation(summary = "Baixar Resumo de Fecho(s) de Caixa em PDF")
    public void gerarRelatorioPdf(
            HttpServletResponse response,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) throws IOException {

        if (relatorioService == null) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Serviço de relatório não configurado");
            return;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=relatorio_caixas.pdf");

        LocalDate dInicio = (inicio != null) ? inicio : LocalDate.now().minusDays(30);
        LocalDate dFim = (fim != null) ? fim : LocalDate.now();

        List<CaixaDiario> lista = caixaRepository.findByDataAberturaBetweenOrderByDataAberturaDesc(
                dInicio.atStartOfDay(), dFim.atTime(23, 59, 59)
        );

        relatorioService.exportarPdf(response, lista, dInicio.toString(), dFim.toString());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(summary = "Aceder à Ficha Técnica de um Fecho de Caixa Antigo")
    public ResponseEntity<CaixaDiarioDTO> buscarPorId(@PathVariable Long id) {
        return caixaRepository.findById(id).map(caixaService::converterParaDTOCompleto).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/venda-perdida")
    @Operation(summary = "Alarme de Rotura (Ruptura Física)", description = "O Operador de caixa reporta quando um cliente procurou por algo mas a loja não tinha estoque.")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> registrarVendaPerdida(@RequestBody Map<String, String> payload) {
        VendaPerdida vp = new VendaPerdida();
        vp.setProdutoProcurado(payload.get("produto"));
        vp.setDataRegistro(LocalDateTime.now());
        vendaPerdidaRepository.save(vp);
        return ResponseEntity.ok().build();
    }
}