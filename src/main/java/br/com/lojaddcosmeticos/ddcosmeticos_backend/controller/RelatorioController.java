package br.com.lojaddcosmeticos.ddcosmeticos_backend.controller;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RelatorioEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/relatorios")
public class RelatorioController {

    @Autowired
    private ProdutoRepository produtoRepository;

    @GetMapping("/inventario-auditavel")
    public ResponseEntity<List<RelatorioEstoqueDTO>> gerarInventario() {
        var produtos = produtoRepository.findAll(); // Pega apenas ativos

        List<RelatorioEstoqueDTO> relatorio = produtos.stream()
                .map(p -> new RelatorioEstoqueDTO(
                        p.getCodigoBarras(),
                        p.getDescricao(),
                        p.getQuantidadeEmEstoque(),
                        p.getEstoqueFiscal(),     // Coluna: Com Nota
                        p.getEstoqueNaoFiscal(),  // Coluna: Sem Nota
                        p.getPrecoMedioPonderado(),
                        p.getPrecoMedioPonderado().multiply(new BigDecimal(p.getQuantidadeEmEstoque()))
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(relatorio);
    }
}