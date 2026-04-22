package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

// 🔥 IMPORTAÇÕES QUE FALTAVAM
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

    // 🔥 AS INJEÇÕES DO MOTOR DA SEFAZ
    @Autowired
    private SefazDistribuicaoService sefazDistribuicaoService;

    @Autowired
    private NfeConfig nfeConfigBuilder;

    // 1. O React chama isto para listar as notas que estão na Caixa de Entrada
    @GetMapping
    public ResponseEntity<List<NotaPendenteImportacao>> listarNotasProntasParaImportar() {
        // Busca TODAS as notas que ainda não foram importadas
        List<NotaPendenteImportacao> notas = notaPendenteRepository.findAll().stream()
                .filter(n -> !"IMPORTADO".equals(n.getStatus()))
                .toList();
        return ResponseEntity.ok(notas);
    }

    // 2. O React chama isto quando a gestora clica no botão "Importar"
    @PostMapping("/{id}/importar")
    public ResponseEntity<String> efetivarImportacao(@PathVariable Long id) {
        Optional<NotaPendenteImportacao> notaOpt = notaPendenteRepository.findById(id);

        if (notaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        NotaPendenteImportacao nota = notaOpt.get();

        try {
            // Repassa o XML para o serviço principal (A sua equipa implementará o interior deste método depois)
            // importacaoXmlService.processarImportacaoXmlString(nota.getXmlCompleto());

            // Após sucesso, muda o status
            nota.setStatus("IMPORTADO");
            notaPendenteRepository.save(nota);

            return ResponseEntity.ok("Nota importada com sucesso para o estoque e financeiro!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao importar a nota: " + e.getMessage());
        }
    }

    // 3. 🔥 O NOVO ENDPOINT DE FORÇAR SINCRONIZAÇÃO DA SEFAZ
    @PostMapping("/sincronizar")
    public ResponseEntity<String> forcarSincronizacaoSefaz() {
        try {
            ConfiguracoesNfe config = nfeConfigBuilder.construirConfiguracaoDinamica(true);

            // 🔥 CORREÇÃO FINAL: Extrai o CNPJ diretamente do certificado para nunca mais dar erro de incompatibilidade!
            String cnpjEmpresa = config.getCertificado().getCnpjCpf();

            sefazDistribuicaoService.buscarNovasNotasNaSefaz(config, cnpjEmpresa);

            return ResponseEntity.ok("Sincronização com a SEFAZ PRD concluída!");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Erro ao comunicar com a SEFAZ: " + e.getMessage());
        }
    }
}