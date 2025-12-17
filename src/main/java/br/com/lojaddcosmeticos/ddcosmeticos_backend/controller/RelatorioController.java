package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.InventarioResponseDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioPerdasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Auditoria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.AuditoriaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/relatorios")
@Tag(name = "Relatórios", description = "Endpoints para análise financeira e operacional")
@SecurityRequirement(name = "bearerAuth") // Protege com Cadeado no Swagger
public class RelatorioController {

    @Autowired
    private RelatorioService relatorioService;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private ProdutoRepository produtoRepository; // <--- ESTA ERA A PEÇA QUE FALTAVA

    // 1. Inventário Contábil (Apenas produtos com nota de entrada)
    @GetMapping("/inventario/contabil")
    public ResponseEntity<InventarioResponseDTO> getInventarioContabil() {
        return ResponseEntity.ok(relatorioService.gerarInventarioEstoque(true));
    }

    // 2. Inventário Gerencial (Tudo o que existe fisicamente)
    @GetMapping("/inventario/gerencial")
    public ResponseEntity<InventarioResponseDTO> getInventarioGerencial() {
        return ResponseEntity.ok(relatorioService.gerarInventarioEstoque(false));
    }

    // 3. Relatório de Motivos de Perdas (Gráfico de Pizza)
    @GetMapping("/perdas/motivos")
    public ResponseEntity<List<RelatorioPerdasDTO>> getPerdasPorMotivo() {
        return ResponseEntity.ok(relatorioService.gerarRelatorioPerdasPorMotivo());
    }

    // 4. Auditoria de Ajustes (O "Livro Negro" do Estoque)
    @GetMapping("/auditoria/ajustes-estoque")
    public ResponseEntity<List<Auditoria>> getHistoricoAjustes() {
        List<Auditoria> ajustes = auditoriaRepository.findAll().stream()
                .filter(a -> a.getTipoEvento().startsWith("INVENTARIO_") || a.getTipoEvento().equals("ESTOQUE_ENTRADA"))
                .toList();
        return ResponseEntity.ok(ajustes);
    }

    // 5. Relatório Fiscal: Produtos Monofásicos (Para enviar ao Contador)
    @GetMapping("/fiscal/monofasicos")
    public ResponseEntity<List<Map<String, Object>>> getRelatorioMonofasicos() {
        List<Produto> produtos = produtoRepository.findAllByAtivoTrue();

        List<Map<String, Object>> relatorio = produtos.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("codigo", p.getCodigoBarras());
            map.put("produto", p.getDescricao());
            map.put("ncm", p.getNcm());
            map.put("monofasico", p.isMonofasico()); // TRUE = Economia de imposto
            map.put("status", p.isMonofasico() ? "ISENTO DE PIS/COFINS (MONOFÁSICO)" : "TRIBUTADO NORMAL");
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(relatorio);
    }

    @GetMapping("/vendas")
    @Operation(summary = "Relatório Financeiro de Vendas", description = "Retorna faturamento, custos e lucro em um período.")
    public ResponseEntity<RelatorioVendasDTO> getRelatorioVendas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim
    ) {
        // Se não informar data, pega o dia de HOJE
        if (inicio == null) inicio = LocalDate.now();
        if (fim == null) fim = LocalDate.now();

        RelatorioVendasDTO relatorio = relatorioService.gerarRelatorioVendas(inicio, fim);
        return ResponseEntity.ok(relatorio);
    }
}