package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;

@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    /**
     * Busca um produto por código de barras.
     * Lança ResourceNotFoundException se não encontrar.
     */
    public Produto buscarPorCodigoBarras(String codigoBarras) {
        // CORREÇÃO: Usar .orElseThrow() para tratar o Optional retornado pelo repositório
        return produtoRepository.findByCodigoBarras(codigoBarras)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado com código de barras: " + codigoBarras));
    }

    /**
     * Importa produtos de um arquivo CSV.
     */
    public int importarProdutosCSV(String caminhoArquivo) {
        int contador = 0;
        String delimitador = ";";

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            br.readLine(); // Pula cabeçalho
            String linha;

            while ((linha = br.readLine()) != null) {
                String linhaLimpa = linha.replace("\"", "");
                String[] campos = linhaLimpa.split(delimitador);

                if (campos.length < 24) continue;

                Produto produto = new Produto();
                produto.setCodigoBarras(campos[0].trim());
                produto.setDescricao(campos[2].trim());

                BigDecimal precoCusto = new BigDecimal(campos[3].replace(",", "."));
                produto.setPrecoCustoInicial(precoCusto);
                produto.setPrecoMedioPonderado(precoCusto);

                produto.setPrecoVendaVarejo(new BigDecimal(campos[4].replace(",", ".")));
                produto.setQuantidadeEmEstoque(new BigDecimal(campos[13].replace(",", ".")));
                produto.setNcm(campos[20].trim());

                String origem = campos[21].trim();
                produto.setOrigem(origem);
                produto.setPossuiNfEntrada(origem.equals("NACIONAL"));

                produtoRepository.save(produto);
                contador++;
            }

        } catch (Exception e) {
            System.err.println("Erro durante a importação do CSV: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }

        return contador;
    }
}