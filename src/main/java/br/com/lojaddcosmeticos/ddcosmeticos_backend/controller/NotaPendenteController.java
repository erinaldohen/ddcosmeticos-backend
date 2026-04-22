package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.NotaPendenteImportacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.NotaPendenteImportacaoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ImportacaoXmlService; // Aquele serviço que criámos antes!
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/estoque/notas-pendentes")
public class NotaPendenteController {

    @Autowired
    private NotaPendenteImportacaoRepository notaPendenteRepository;

    // TODO: A equipa de Backend precisa garantir que esta classe existe conforme desenhámos no passo da Importação.
    @Autowired
    private ImportacaoXmlService importacaoXmlService;

    // 1. O React chama isto para listar as notas que estão na Caixa de Entrada
    @GetMapping
    public ResponseEntity<List<NotaPendenteImportacao>> listarNotasProntasParaImportar() {
        // Busca apenas as notas que o robô baixou o XML completo
        List<NotaPendenteImportacao> notas = notaPendenteRepository.findAll().stream()
                .filter(n -> "PRONTO_IMPORTACAO".equals(n.getStatus()))
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
            // Repassa o XML (String) para o serviço principal de importação processar o stock e financeiro
            // importacaoXmlService.processarImportacaoXmlString(nota.getXmlCompleto());

            // Após sucesso, muda o status para não aparecer mais na caixa de entrada
            nota.setStatus("IMPORTADO");
            notaPendenteRepository.save(nota);

            return ResponseEntity.ok("Nota importada com sucesso para o estoque e financeiro!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao importar a nota: " + e.getMessage());
        }
    }
}