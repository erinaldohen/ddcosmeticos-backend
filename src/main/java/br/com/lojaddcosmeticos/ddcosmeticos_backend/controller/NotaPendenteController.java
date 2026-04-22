package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.config.NfeConfig;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.NotaPendenteImportacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.NotaPendenteImportacaoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ImportacaoXmlService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.SefazDistribuicaoService;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/estoque/notas-pendentes")
public class NotaPendenteController {

    @Autowired
    private NotaPendenteImportacaoRepository notaPendenteRepository;

    @Autowired
    private ImportacaoXmlService importacaoXmlService;

    @Autowired
    private SefazDistribuicaoService sefazDistribuicaoService;

    @Autowired
    private NfeConfig nfeConfigBuilder;

    // 🔥 ROTA ÚNICA DE LISTAGEM COM FILTRO DE DATAS OPCIONAIS
    @GetMapping
    public ResponseEntity<List<NotaPendenteImportacao>> listarNotasProntasParaImportar(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {

        // Busca todas as notas pendentes
        List<NotaPendenteImportacao> notas = notaPendenteRepository.findAll().stream()
                .filter(n -> !"IMPORTADO".equals(n.getStatus()))
                .toList();

        // Filtra por período se as datas vieram na requisição
        if (dataInicio != null && !dataInicio.isEmpty() && dataFim != null && !dataFim.isEmpty()) {
            notas = notas.stream().filter(nota -> {
                try {
                    Object dataEmissaoObj = nota.getDataEmissao() != null ? nota.getDataEmissao() : nota.getDataCaptura();
                    if (dataEmissaoObj == null) return false;
                    String dataString = dataEmissaoObj.toString().substring(0, 10);
                    return dataString.compareTo(dataInicio) >= 0 && dataString.compareTo(dataFim) <= 0;
                } catch (Exception e) {
                    return true;
                }
            }).toList();
        }

        return ResponseEntity.ok(notas);
    }

    // 2. Importação efetiva
    @PostMapping("/{id}/importar")
    public ResponseEntity<String> efetivarImportacao(@PathVariable Long id) {
        Optional<NotaPendenteImportacao> notaOpt = notaPendenteRepository.findById(id);

        if (notaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        NotaPendenteImportacao nota = notaOpt.get();

        try {
            // importacaoXmlService.processarImportacaoXmlString(nota.getXmlCompleto());
            nota.setStatus("IMPORTADO");
            notaPendenteRepository.save(nota);
            return ResponseEntity.ok("Nota importada com sucesso para o estoque e financeiro!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao importar a nota: " + e.getMessage());
        }
    }

    // 3. Forçar Sincronização
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

    // 4. 🔥 O NOVO "SNIPER": Busca Direta por Chave de Acesso
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
}