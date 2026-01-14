package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ImportacaoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Transactional
    public void importarProdutos(MultipartFile arquivo) {
        // Tenta usar ISO-8859-1 (comum em CSVs do Excel no Brasil)
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(arquivo.getInputStream(), StandardCharsets.ISO_8859_1))) {

            String linha = reader.readLine(); // Lê a primeira linha (Cabeçalho)
            if (linha == null) return;

            // --- 1. MAPEAMENTO DINÂMICO DE COLUNAS ---
            // Isso resolve o problema de trocar CEST por ORIGEM se a ordem mudar
            String[] headers = linha.split(";");
            Map<String, Integer> mapaColunas = new HashMap<>();

            for (int i = 0; i < headers.length; i++) {
                // Remove espaços e deixa maiúsculo para garantir o "match"
                mapaColunas.put(headers[i].trim().toUpperCase(), i);
            }

            // Verifica colunas obrigatórias mínimas
            if (!mapaColunas.containsKey("CODIGO_BARRAS") && !mapaColunas.containsKey("EAN")) {
                throw new RuntimeException("O arquivo CSV precisa ter uma coluna 'CODIGO_BARRAS' ou 'EAN'.");
            }

            // --- 2. LEITURA DAS LINHAS ---
            while ((linha = reader.readLine()) != null) {
                // split(..., -1) garante que colunas vazias no final não sejam ignoradas
                String[] dados = linha.split(";", -1);

                // Evita erro se a linha estiver em branco ou incompleta
                if (dados.length < 2) continue;

                // Identifica o EAN (Chave do produto)
                int indexEan = mapaColunas.getOrDefault("CODIGO_BARRAS", mapaColunas.getOrDefault("EAN", -1));
                if (indexEan == -1 || indexEan >= dados.length) continue;

                String ean = dados[indexEan].replaceAll("\\D", ""); // Limpa caracteres não numéricos
                if (ean.isBlank()) continue;

                // Busca existente ou cria novo
                Optional<Produto> existenteOpt = produtoRepository.findByCodigoBarras(ean);
                Produto produto = existenteOpt.orElse(new Produto());

                if (produto.getId() == null) {
                    produto.setCodigoBarras(ean);
                    produto.setAtivo(true);
                    produto.setQuantidadeEmEstoque(0);
                }

                // --- PREENCHIMENTO SEGURO ---

                // DESCRICAO
                if (mapaColunas.containsKey("DESCRICAO")) {
                    produto.setDescricao(dados[mapaColunas.get("DESCRICAO")].toUpperCase().trim());
                }

                // PRECO DE VENDA
                if (mapaColunas.containsKey("PRECO_VENDA")) {
                    String val = dados[mapaColunas.get("PRECO_VENDA")].replace("R$", "").replace(".", "").replace(",", ".").trim();
                    try {
                        produto.setPrecoVenda(new BigDecimal(val));
                    } catch (Exception e) {
                        if (produto.getId() == null) produto.setPrecoVenda(BigDecimal.ZERO);
                    }
                }

                // NCM
                if (mapaColunas.containsKey("NCM")) {
                    String ncm = dados[mapaColunas.get("NCM")].replaceAll("\\D", "");
                    produto.setNcm(ncm.isEmpty() ? null : ncm);
                }

                // CEST (Correção do seu problema anterior)
                if (mapaColunas.containsKey("CEST")) {
                    String cest = dados[mapaColunas.get("CEST")].replaceAll("\\D", "");
                    produto.setCest(cest.isEmpty() ? null : cest);
                }

                // ORIGEM (Correção do seu problema anterior)
                if (mapaColunas.containsKey("ORIGEM")) {
                    String origem = dados[mapaColunas.get("ORIGEM")].replaceAll("\\D", "");
                    // Pega só o primeiro dígito e garante que não é vazio
                    produto.setOrigem(origem.isEmpty() ? "0" : origem.substring(0, 1));
                }

                // Salva no banco
                produtoRepository.save(produto);
            }

        } catch (Exception e) {
            log.error("Erro ao importar CSV", e);
            throw new RuntimeException("Erro ao processar arquivo: " + e.getMessage());
        }
    }
}