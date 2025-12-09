package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

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
     * Importa produtos de um arquivo CSV, calculando o PMP inicial.
     * @param caminhoArquivo O caminho absoluto ou relativo para o arquivo CSV.
     * @return O número de produtos importados.
     */
    public Produto buscarPorCodigoBarras(String codigoBarras) {
        // O método findByCodigoBarras foi definido na interface ProdutoRepository
        return produtoRepository.findByCodigoBarras(codigoBarras);
    }

    public int importarProdutosCSV(String caminhoArquivo) {
        int contador = 0;
        // O delimitador é o ponto-e-vírgula (;) conforme o arquivo
        String delimitador = ";";

        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            // Pula a linha de cabeçalho
            br.readLine();
            String linha;

            while ((linha = br.readLine()) != null) {
                // Remove as aspas duplas de toda a linha para facilitar o parsing
                String linhaLimpa = linha.replace("\"", "");
                String[] campos = linhaLimpa.split(delimitador);

                if (campos.length < 24) continue; // Garante que temos colunas suficientes

                Produto produto = new Produto();

                // 1. Mapeamento dos Campos Críticos (Baseado na análise)
                // O CSV tem 24 colunas (do 0 ao 23) antes das descrições longas

                // Coluna 0: Código de Barras
                produto.setCodigoBarras(campos[0].trim());

                // Coluna 2: Descrição
                produto.setDescricao(campos[2].trim());

                // Coluna 3: Preço de Custo (usado para PMP inicial)
                BigDecimal precoCusto = new BigDecimal(campos[3].replace(",", "."));
                produto.setPrecoCustoInicial(precoCusto);

                // Requisito: PMP Inicial = Preço de Custo
                produto.setPrecoMedioPonderado(precoCusto);

                // Coluna 4: Preço Venda Varejo
                produto.setPrecoVendaVarejo(new BigDecimal(campos[4].replace(",", ".")));

                // Coluna 13: Quantidade em Estoque
                produto.setQuantidadeEmEstoque(new BigDecimal(campos[13].replace(",", ".")));

                // Coluna 20: NCM
                produto.setNcm(campos[20].trim());

                // Coluna 21: Origem (usaremos para sugerir a NF de Entrada)
                String origem = campos[21].trim();
                produto.setOrigem(origem);
                // Sugestão: Apenas produtos NACIONAL são marcados por padrão como tendo NF de entrada
                produto.setPossuiNfEntrada(origem.equals("NACIONAL"));

                // Salva o produto (JPA)
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