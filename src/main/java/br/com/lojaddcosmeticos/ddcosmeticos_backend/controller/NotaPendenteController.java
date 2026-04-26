package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.NfeConfig;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.NotaPendenteImportacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.NotaPendenteImportacaoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ImportacaoXmlService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.SefazDistribuicaoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/estoque/notas-pendentes")
@CrossOrigin(origins = "*")
public class NotaPendenteController {

    @Autowired
    private NotaPendenteImportacaoRepository notaPendenteRepository;

    @Autowired
    private ImportacaoXmlService importacaoXmlService;

    @Autowired
    private SefazDistribuicaoService sefazDistribuicaoService;

    @Autowired
    private NfeConfig nfeConfigBuilder;

    // 🔥 ATUALIZADO: Agora suporta Filtros de Emissão, Paginação e Inclusão de Histórico (Toggle Switch)
    @GetMapping
    public ResponseEntity<Page<NotaPendenteImportacao>> listarNotasProntasParaImportar(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(defaultValue = "false") boolean incluirImportadas,
            @PageableDefault(size = 50, sort = "dataCaptura") Pageable pageable) {

        Page<NotaPendenteImportacao> notasPage;

        // Lógica de Filtro com Datas e Status
        if (dataInicio != null && !dataInicio.isEmpty() && dataFim != null && !dataFim.isEmpty()) {
            if (incluirImportadas) {
                // Traz TUDO dentro do período
                notasPage = notaPendenteRepository.findByDataEmissaoBetweenOrderByDataCapturaDesc(dataInicio, dataFim, pageable);
            } else {
                // Traz apenas as PENDENTES dentro do período
                notasPage = notaPendenteRepository.findByDataEmissaoBetweenAndStatusNotOrderByDataCapturaDesc(dataInicio, dataFim, "IMPORTADA", pageable);
            }
        } else {
            // Busca Padrão (Sem datas)
            if (incluirImportadas) {
                notasPage = notaPendenteRepository.findAllByOrderByDataCapturaDesc(pageable);
            } else {
                notasPage = notaPendenteRepository.findAllByStatusNotOrderByDataCapturaDesc("IMPORTADA", pageable);
            }
        }

        return ResponseEntity.ok(notasPage);
    }

    @PostMapping("/{id}/importar")
    public ResponseEntity<String> efetivarImportacao(@PathVariable Long id) {
        Optional<NotaPendenteImportacao> notaOpt = notaPendenteRepository.findById(id);

        if (notaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        NotaPendenteImportacao nota = notaOpt.get();

        try {
            // A linha de processamento antigo comentada, pois quem salva os itens agora é o React
            nota.setStatus("IMPORTADA"); // 🔥 PADRONIZADO PARA "IMPORTADA" (Feminino) para bater com o Frontend
            notaPendenteRepository.save(nota);
            return ResponseEntity.ok("Nota importada com sucesso para o estoque e financeiro!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao importar a nota: " + e.getMessage());
        }
    }

    @PostMapping("/sincronizar")
    public ResponseEntity<String> forcarSincronizacaoSefaz() {
        try {
            ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);
            String cnpjEmpresa = config.getCertificado().getCnpjCpf();
            sefazDistribuicaoService.buscarNovasNotasNaSefaz(config, cnpjEmpresa);
            return ResponseEntity.ok("Sincronização com a SEFAZ PRD concluída!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Erro ao comunicar com a SEFAZ: " + e.getMessage());
        }
    }

    @PostMapping("/buscar-chave/{chave}")
    public ResponseEntity<String> buscarNotaPorChave(@PathVariable String chave) {
        try {
            ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);
            String cnpjEmpresa = config.getCertificado().getCnpjCpf();
            String mensagem = sefazDistribuicaoService.buscarNotaPorChaveEspecifca(config, cnpjEmpresa, chave);
            return ResponseEntity.ok(mensagem);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/manifestar")
    public ResponseEntity<String> solicitarXmlCompletoSeFaltante(@PathVariable Long id) {
        try {
            NotaPendenteImportacao nota = notaPendenteRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Nota não encontrada na base."));

            ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);
            String msg = sefazDistribuicaoService.manifestarEBaixarXmlCompleto(config, nota.getChaveAcesso());
            return ResponseEntity.ok(msg);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Erro ao processar a Ciência da Operação: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/xml-parse")
    public ResponseEntity<?> fazerParseDeXmlGuardado(@PathVariable Long id) {
        Optional<NotaPendenteImportacao> notaOpt = notaPendenteRepository.findById(id);

        if (notaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            String xml = notaOpt.get().getXmlCompleto();
            Object resultadoParse = importacaoXmlService.simularImportacaoXmlString(xml);
            return ResponseEntity.ok(resultadoParse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao ler o XML da base: " + e.getMessage());
        }
    }
}