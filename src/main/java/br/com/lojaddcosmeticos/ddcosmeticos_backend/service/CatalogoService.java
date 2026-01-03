package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoVisualDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CatalogoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    public List<ProdutoVisualDTO> buscarProdutos(String termo) {
        List<Produto> produtos;

        if (termo == null || termo.trim().isEmpty()) {
            // Se não digitar nada, traz os ativos (limitado a 50 para não travar)
            // Num sistema real usaria paginação, aqui faremos simples
            produtos = produtoRepository.findAll().stream()
                    .filter(Produto::isAtivo)
                    .limit(50)
                    .collect(Collectors.toList());
        } else {
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

        p.atualizarSaldoTotal(); // Garante cálculo fiscal + não fiscal
        int qtd = p.getQuantidadeEmEstoque();

        if (qtd <= 0) {
            status = "ESGOTADO";
            cor = "RED";
        } else if (qtd <= p.getEstoqueMinimo()) {
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