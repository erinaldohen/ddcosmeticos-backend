package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.NfeConfig;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.NotaPendenteImportacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.NotaPendenteImportacaoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ImportacaoXmlService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.SefazDistribuicaoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/estoque/notas-pendentes")
@Tag(name = "Estoque / Recepção Fiscal", description = "Monitorização e Importação de Notas Fiscais emitidas contra o CNPJ (Manifestação de Destinatário)")
public class NotaPendenteController {

    @Autowired private NotaPendenteImportacaoRepository notaPendenteRepository;
    @Autowired private ImportacaoXmlService importacaoXmlService;
    @Autowired private SefazDistribuicaoService sefazDistribuicaoService;
    @Autowired private NfeConfig nfeConfigBuilder;

    @GetMapping
    @Operation(summary = "Lista Notas Fiscais de Entrada", description = "Retorna XMLs prontos para importar ou o histórico de recebimentos.")
    public ResponseEntity<Page<NotaPendenteImportacao>> listarNotasProntasParaImportar(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim,
            @RequestParam(defaultValue = "false") boolean incluirImportadas,
            @PageableDefault(size = 50, sort = "dataCaptura") Pageable pageable) {

        boolean isFiltroDataValido = (dataInicio != null && !dataInicio.isEmpty() && dataFim != null && !dataFim.isEmpty());
        Page<NotaPendenteImportacao> notasPage;

        if (isFiltroDataValido) {
            notasPage = incluirImportadas
                    ? notaPendenteRepository.findByDataEmissaoBetweenOrderByDataCapturaDesc(dataInicio, dataFim, pageable)
                    : notaPendenteRepository.findByDataEmissaoBetweenAndStatusNotOrderByDataCapturaDesc(dataInicio, dataFim, "IMPORTADA", pageable);
        } else {
            notasPage = incluirImportadas
                    ? notaPendenteRepository.findAllByOrderByDataCapturaDesc(pageable)
                    : notaPendenteRepository.findAllByStatusNotOrderByDataCapturaDesc("IMPORTADA", pageable);
        }

        return ResponseEntity.ok(notasPage);
    }

    @PostMapping("/{id}/importar")
    @Operation(summary = "Efetivar Entrada de XML", description = "Marca o XML da nuvem como importado internamente.")
    public ResponseEntity<String> efetivarImportacao(@PathVariable Long id) {
        Optional<NotaPendenteImportacao> notaOpt = notaPendenteRepository.findById(id);
        if (notaOpt.isEmpty()) return ResponseEntity.notFound().build();

        try {
            NotaPendenteImportacao nota = notaOpt.get();
            nota.setStatus("IMPORTADA");
            notaPendenteRepository.save(nota);
            return ResponseEntity.ok("Nota registada como importada com sucesso no sistema local!");
        } catch (Exception e) {
            log.error("Erro ao efetivar importação", e);
            return ResponseEntity.badRequest().body("Erro ao importar a nota: " + e.getMessage());
        }
    }

    @PostMapping("/sincronizar")
    @Operation(summary = "Sincronização Manual SEFAZ", description = "Varre a base nacional de NF-e à procura de novas notas emitidas para o CNPJ (Fornecedores).")
    public ResponseEntity<String> forcarSincronizacaoSefaz() {
        try {
            ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);
            sefazDistribuicaoService.buscarNovasNotasNaSefaz(config, config.getCertificado().getCnpjCpf());
            return ResponseEntity.ok("Varredura na Nuvem da SEFAZ Nacional (MDe) concluída!");
        } catch (Exception e) {
            log.error("Falha na sincronização Sefaz: ", e);
            return ResponseEntity.internalServerError().body("Erro de comunicação com a SEFAZ: " + e.getMessage());
        }
    }

    @PostMapping("/buscar-chave/{chave}")
    @Operation(summary = "Download Forçado por Chave", description = "Busca uma nota fiscal específica a partir da sua chave de acesso de 44 dígitos.")
    public ResponseEntity<String> buscarNotaPorChave(@PathVariable String chave) {
        try {
            ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);
            String mensagem = sefazDistribuicaoService.buscarNotaPorChaveEspecifca(config, config.getCertificado().getCnpjCpf(), chave);
            return ResponseEntity.ok(mensagem);
        } catch (Exception e) {
            log.error("Falha na busca forçada por chave: ", e);
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/manifestar")
    @Operation(summary = "Ciência da Operação", description = "Envia evento de ciência para a Receita para poder fazer o download do XML Completo do Fornecedor.")
    public ResponseEntity<String> solicitarXmlCompletoSeFaltante(@PathVariable Long id) {
        try {
            NotaPendenteImportacao nota = notaPendenteRepository.findById(id).orElseThrow(() -> new RuntimeException("Registo Cloud não encontrado."));
            ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);
            String msg = sefazDistribuicaoService.manifestarEBaixarXmlCompleto(config, nota.getChaveAcesso());
            return ResponseEntity.ok(msg);
        } catch (Exception e) {
            log.error("Erro na manifestação MDe: ", e);
            return ResponseEntity.badRequest().body("Erro ao emitir Ciência da Operação: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/xml-parse")
    @Operation(summary = "Ler/Decifrar XML Criptografado", description = "Lê o ficheiro RAW XML e devolve os dados estruturados numa árvore JSON para o Frontend.")
    public ResponseEntity<?> fazerParseDeXmlGuardado(@PathVariable Long id) {
        try {
            Optional<NotaPendenteImportacao> notaOpt = notaPendenteRepository.findById(id);
            if (notaOpt.isEmpty()) return ResponseEntity.notFound().build();

            return ResponseEntity.ok(importacaoXmlService.simularImportacaoXmlString(notaOpt.get().getXmlCompleto()));
        } catch (Exception e) {
            log.error("Erro de Parsing XML: ", e);
            return ResponseEntity.badRequest().body("Erro ao analisar a estrutura do ficheiro XML (O Ficheiro pode estar corrompido ou ser apenas o Resumo NSU): " + e.getMessage());
        }
    }
}