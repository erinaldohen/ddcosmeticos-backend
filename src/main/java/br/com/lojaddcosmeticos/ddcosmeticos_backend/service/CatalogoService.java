package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoVisualDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CatalogoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Transactional(readOnly = true)
    public List<ProdutoVisualDTO> buscarProdutos(String termo) {
        List<Produto> produtos;

        if (termo == null || termo.trim().isEmpty()) {
            // OTIMIZAÇÃO: Busca direto no banco apenas os 50 ativos mais recentes
            // Antes fazia findAll() -> filtrava na memoria (lento)
            produtos = produtoRepository.findTop50ByAtivoTrueOrderByIdDesc();
        } else {
            // Usa a query restaurada que busca em Marca, Categoria, Nome e EAN
            produtos = produtoRepository.buscarInteligente(termo);
        }

        return produtos.stream()
                .map(this::converterParaVisual)
                .collect(Collectors.toList());
    }

    private ProdutoVisualDTO converterParaVisual(Produto p) {
        // Lógica de UX para o Estoque
        String status = "DISPONÍVEL";
        String cor = "GREEN";

        // Proteção contra null pointer se o método não existir na Model atual
        // p.atualizarSaldoTotal();

        int qtd = p.getQuantidadeEmEstoque() != null ? p.getQuantidadeEmEstoque() : 0;
        int min = p.getEstoqueMinimo() != null ? p.getEstoqueMinimo() : 0;

        if (qtd <= 0) {
            status = "ESGOTADO";
            cor = "RED";
        } else if (qtd <= min) {
            status = "ÚLTIMAS UNIDADES";
            cor = "ORANGE";
        }

        // Define uma imagem padrão se não tiver (Placeholder)
        String imagem = (p.getUrlImagem() != null && !p.getUrlImagem().isEmpty())
                ? p.getUrlImagem()
                : "https://via.placeholder.com/150?text=Sem+Imagem";

        return new ProdutoVisualDTO(
                p.getId(),
                p.getCodigoBarras(),
                p.getDescricao(),
                p.getMarca(),
                p.getPrecoVenda(),
                imagem,
                status,
                cor
        );
    }
}