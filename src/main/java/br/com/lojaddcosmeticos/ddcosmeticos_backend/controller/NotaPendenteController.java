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
import java.util.stream.Collectors;

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

    @Autowired
    private NotaPendenteImportacaoRepository notaPendenteImportacaoRepository;

    @GetMapping
    public ResponseEntity<List<NotaPendenteImportacao>> listarNotasProntasParaImportar(
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {

        List<NotaPendenteImportacao> notas = notaPendenteImportacaoRepository.buscarPendentesOrdenadas();

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
            }).collect(Collectors.toList());
        }

        // 🔥 O TRUQUE DE FORMATAÇÃO DO CNPJ SEM MEXER NO BANCO 🔥
        // A tela vai receber o CNPJ bonitinho, mas no banco continua sujo (14 chars) para não dar erro!
        notas.forEach(nota -> {
            if (nota.getCnpjFornecedor() != null && nota.getCnpjFornecedor().length() >= 14 && !nota.getCnpjFornecedor().contains(".")) {
                String num = nota.getCnpjFornecedor().replaceAll("\\D", "");
                if(num.length() == 14){
                    nota.setCnpjFornecedor(num.substring(0, 2) + "." + num.substring(2, 5) + "." + num.substring(5, 8) + "/" + num.substring(8, 12) + "-" + num.substring(12, 14));
                }
            }
        });

        return ResponseEntity.ok(notas);
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
            nota.setStatus("IMPORTADO");
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