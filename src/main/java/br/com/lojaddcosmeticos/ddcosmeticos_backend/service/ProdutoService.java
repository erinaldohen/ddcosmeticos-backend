package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de Gestão de Produtos da DD Cosméticos.
 * Orquestra desde o cadastro individual até a importação fiscal massiva.
 */
@Slf4j
@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    /**
     * Busca um produto pelo código de barras.
     * @throws ResourceNotFoundException se o item não existir.
     */
    @Transactional(readOnly = true)
    public Produto buscarPorCodigoBarras(String ean) {
        return produtoRepository.findByCodigoBarras(ean)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não localizado com o EAN: " + ean));
    }

    /**
     * Importação Massiva de Estoque com Inteligência Fiscal.
     * Mapeia campos do Excel e automatiza classificações tributárias.
     */
    @Transactional
    public void importarEstoqueCSV(MultipartFile file) {
        log.info("Iniciando importação de ficheiro: {}", file.getOriginalFilename());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // Salta o cabeçalho do Excel/CSV
            List<Produto> lote = new ArrayList<>();
            String linha;
            int contador = 0;

            while ((linha = reader.readLine()) != null) {
                // Regex avançada: separa por vírgula, mas ignora vírgulas dentro de aspas (ex: "Shampoo, 200ml")
                String[] col = linha.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                if (col.length < 24) continue;

                String ean = col[0].trim();
                if (ean.isEmpty()) continue;

                // 1. Lógica de Atualização (Upsert)
                Produto produto = produtoRepository.findByCodigoBarras(ean).orElse(new Produto());

                produto.setCodigoBarras(ean);
                produto.setDescricao(col[2].replace("\"", "").trim());

                // 2. Preços e Stock
                BigDecimal custo = parseBigDecimal(col[3]);
                produto.setPrecoCustoInicial(custo);
                produto.setPrecoMedioPonderado(custo);
                produto.setPrecoVenda(parseBigDecimal(col[4]));
                produto.setQuantidadeEmEstoque(parseBigDecimal(col[13]));

                // 3. Unidade e Ativo
                produto.setUnidade(col[7].isEmpty() ? "UN" : col[7].trim().toUpperCase());
                produto.setAtivo(col[8].equalsIgnoreCase("Sim"));
                produto.setEstoqueMinimo(parseBigDecimal(col[12])); // Mapeia a coluna 12 do CSV
                produto.setQuantidadeEmEstoque(parseBigDecimal(col[13]));

                // 4. Mapeamento Fiscal de Pernambuco
                String ncmLimpo = col[20].replaceAll("[^0-9]", "");
                produto.setNcm(ncmLimpo);
                produto.setCest(col[23].replaceAll("[^0-9]", ""));

                // Origem: Converte descrição para código SEFAZ (0-Nacional, 1-Importado)
                produto.setOrigem(col[22].toUpperCase().contains("NACIONAL") ? "0" : "1");

                // 5. Inteligência Monofásica (Prefixo NCM - Lei 10.147/00)
                // Se o NCM começa com 3303, 3304, 3305 ou 3307, o produto é isento de PIS/COFINS na venda.
                if (ncmLimpo.startsWith("3303") || ncmLimpo.startsWith("3304") ||
                        ncmLimpo.startsWith("3305") || ncmLimpo.startsWith("3307")) {
                    produto.setMonofasico(true);
                }

                lote.add(produto);
                contador++;

                // Processamento em lote de 50 para otimizar o MySQL
                if (lote.size() >= 50) {
                    produtoRepository.saveAll(lote);
                    lote.clear();
                    log.debug("Lote de 50 produtos processado...");
                }
            }

            if (!lote.isEmpty()) {
                produtoRepository.saveAll(lote);
            }

            log.info("Importação finalizada com sucesso! {} produtos atualizados/inseridos.", contador);

        } catch (Exception e) {
            log.error("Erro crítico na importação: ", e);
            throw new ValidationException("Erro ao processar ficheiro de estoque: " + e.getMessage());
        }
    }

    /**
     * Auxiliar para converter String do CSV em BigDecimal com segurança.
     */
    private BigDecimal parseBigDecimal(String valor) {
        if (valor == null || valor.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            // Remove possíveis aspas e substitui vírgula por ponto se necessário
            String limpo = valor.replace("\"", "").trim();
            return new BigDecimal(limpo);
        } catch (Exception e) {
            log.warn("Falha ao converter valor numérico: {}. Usando ZERO.", valor);
            return BigDecimal.ZERO;
        }
    }
}