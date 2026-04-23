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

import java.util.Comparator;
import java.util.List;
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

    // 🔥 ROTA ÚNICA DE LISTAGEM COM FILTRO DE DATAS OPCIONAIS (CORRIGIDA)
    @GetMapping
    public ResponseEntity<List<NotaPendenteImportacao>> listarNotasProntasParaImportar(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {

        // Busca TODAS as notas, ordena da mais recente para a mais antiga
        List<NotaPendenteImportacao> notas = notaPendenteRepository.findAll().stream()
                .filter(n -> !"IMPORTADO".equals(n.getStatus()))
                .sorted(Comparator.comparing(NotaPendenteImportacao::getDataCaptura).reversed())
                .toList();

        // Filtra por período SE as datas vieram na requisição (Blindagem contra Nulos)
        if (dataInicio != null && !dataInicio.isEmpty() && dataFim != null && !dataFim.isEmpty()) {
            notas = notas.stream().filter(nota -> {
                try {
                    // Usa a data de emissão se existir, se não, usa a de captura para nunca falhar
                    Object dataEmissaoObj = nota.getDataEmissao() != null ? nota.getDataEmissao() : nota.getDataCaptura();
                    if (dataEmissaoObj == null) return false; // Falha de segurança

                    String dataString = dataEmissaoObj.toString().substring(0, 10);
                    return dataString.compareTo(dataInicio) >= 0 && dataString.compareTo(dataFim) <= 0;
                } catch (Exception e) {
                    return true; // Se der erro ao ler a data, deixa passar para a tela
                }
            }).toList();
        }

        return ResponseEntity.ok(notas);
    }

    // 2. Importação efetiva (No NotaPendenteController)
    @PostMapping("/{id}/importar")
    public ResponseEntity<String> efetivarImportacao(@PathVariable Long id) {
        Optional<NotaPendenteImportacao> notaOpt = notaPendenteRepository.findById(id);

        if (notaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        NotaPendenteImportacao nota = notaOpt.get();

        try {
            // 🔥 REMOVA AS BARRAS DE COMENTÁRIO DESTA LINHA:
            importacaoXmlService.processarImportacaoXmlString(nota.getXmlCompleto());

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
    @GetMapping("/{id}/xml-parse")
    public ResponseEntity<?> fazerParseDeXmlGuardado(@PathVariable Long id) {
        Optional<NotaPendenteImportacao> notaOpt = notaPendenteRepository.findById(id);

        if (notaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            String xml = notaOpt.get().getXmlCompleto();
            // O ImportacaoXmlService já tem uma função para fazer parse de String (usada no botão anterior)
            Object resultadoParse = importacaoXmlService.simularImportacaoXmlString(xml);

            // Depois que for tudo conferido na tela, a nota muda de status
            return ResponseEntity.ok(resultadoParse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao ler o XML da base: " + e.getMessage());
        }
    }
}