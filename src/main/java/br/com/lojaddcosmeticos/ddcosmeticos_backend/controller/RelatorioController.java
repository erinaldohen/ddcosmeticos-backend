package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemAbcDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioVendasDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.RelatorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;

    @GetMapping("/vendas")
    public RelatorioVendasDTO obterRelatorio(@RequestParam LocalDate inicio, @RequestParam LocalDate fim) {
        // Sem try-catch: erros são tratados pelo GlobalExceptionHandler
        return relatorioService.gerarRelatorioVendas(inicio, fim);
    }

    @GetMapping("/curva-abc")
    public List<ItemAbcDTO> obterCurvaAbc() {
        return relatorioService.gerarCurvaAbc();
    }
}