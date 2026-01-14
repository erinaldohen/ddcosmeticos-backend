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
import java.util.Arrays;
import java.util.Optional;

@Slf4j
@Service
public class ImportacaoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Transactional
    public void importarProdutos(MultipartFile arquivo) {
        log.info("Iniciando importação do arquivo: {}", arquivo.getOriginalFilename());

        // Tenta ler com ISO-8859-1 (comum em Excel BR)
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(arquivo.getInputStream(), StandardCharsets.ISO_8859_1))) {

            String linhaCabecalho = reader.readLine();
            if (linhaCabecalho == null) {
                throw new RuntimeException("O arquivo está vazio.");
            }

            // 1. LIMPEZA DE CARACTERES OCULTOS (BOM)
            // Remove o caractere invisível que o Excel coloca no início do arquivo
            if (linhaCabecalho.startsWith("\uFEFF")) {
                linhaCabecalho = linhaCabecalho.substring(1);
            }

            // 2. DETECÇÃO AUTOMÁTICA DE SEPARADOR
            String separador = linhaCabecalho.contains(";") ? ";" : ",";
            log.info("Separador detectado: '{}'", separador);

            String[] headers = linhaCabecalho.split(separador);

            // Log para debug: Mostra o que o Java está "vendo" no cabeçalho
            log.info("Cabeçalhos lidos: " + Arrays.toString(headers));

            // 3. MAPEAMENTO DE ÍNDICES (FLEXÍVEL)
            int idxEan = -1;
            int idxDescricao = -1;
            int idxPreco = -1;
            int idxNcm = -1;
            int idxCest = -1;
            int idxOrigem = -1;

            for (int i = 0; i < headers.length; i++) {
                // Normaliza o texto: Maiúsculo, sem espaços nas pontas e sem aspas
                String h = headers[i].toUpperCase().trim().replace("\"", "");

                if (h.contains("BARRAS") || h.equals("EAN") || h.equals("GTIN")) idxEan = i;
                else if (h.contains("DESC") || h.contains("NOME")) idxDescricao = i;
                else if (h.contains("PRECO") || h.contains("VALOR")) idxPreco = i;
                else if (h.contains("NCM")) idxNcm = i;
                    // Busca por palavra chave (resolve se estiver escrito "COD_CEST" ou "C.E.S.T")
                else if (h.contains("CEST")) idxCest = i;
                    // Busca por palavra chave (resolve se estiver "ORIGEM_MERCADORIA")
                else if (h.contains("ORIGEM")) idxOrigem = i;
            }

            log.info("Mapeamento -> CEST: Coluna {}, ORIGEM: Coluna {}", idxCest, idxOrigem);

            if (idxEan == -1) {
                throw new RuntimeException("Não foi encontrada a coluna de 'CODIGO_BARRAS' ou 'EAN'. Verifique o cabeçalho.");
            }

            // 4. LEITURA DOS DADOS
            String linha;
            int linhaAtual = 1;
            int produtosSalvos = 0;

            while ((linha = reader.readLine()) != null) {
                linhaAtual++;
                // split com -1 preserva colunas vazias no final
                String[] dados = linha.split(separador, -1);

                // Se a linha estiver incompleta, pula
                if (dados.length <= idxEan) continue;

                // Limpeza do EAN
                String ean = dados[idxEan].replaceAll("\\D", "");
                if (ean.isBlank()) continue;

                // Busca ou cria novo
                Optional<Produto> existenteOpt = produtoRepository.findByCodigoBarras(ean);
                Produto produto = existenteOpt.orElse(new Produto());

                if (produto.getId() == null) {
                    produto.setCodigoBarras(ean);
                    produto.setAtivo(true);
                    produto.setQuantidadeEmEstoque(0);
                }

                // Preenchimento Seguro
                if (idxDescricao != -1 && dados.length > idxDescricao)
                    produto.setDescricao(dados[idxDescricao].toUpperCase().trim().replace("\"", ""));

                if (idxPreco != -1 && dados.length > idxPreco) {
                    try {
                        String p = dados[idxPreco].replace("R$", "").replace(".", "").replace(",", ".").trim().replace("\"", "");
                        if (!p.isBlank()) produto.setPrecoVenda(new BigDecimal(p));
                    } catch (Exception ignored) {}
                }

                if (idxNcm != -1 && dados.length > idxNcm) {
                    String n = dados[idxNcm].replaceAll("\\D", "");
                    if (!n.isBlank()) produto.setNcm(n);
                }

                // --- CORREÇÃO DO CEST E ORIGEM ---

                // Pega CEST apenas se a coluna foi encontrada e existe dado na linha
                if (idxCest != -1 && dados.length > idxCest) {
                    String c = dados[idxCest].replaceAll("\\D", ""); // Remove pontos
                    if (!c.isBlank()) {
                        produto.setCest(c);
                    }
                }

                // Pega ORIGEM apenas se a coluna foi encontrada
                if (idxOrigem != -1 && dados.length > idxOrigem) {
                    String o = dados[idxOrigem].replaceAll("\\D", "");
                    // Origem é sempre 1 dígito (0, 1, 2...). Se vier vazio, assume 0 (Nacional)
                    produto.setOrigem(o.isEmpty() ? "0" : o.substring(0, 1));
                }

                produtoRepository.save(produto);
                produtosSalvos++;
            }

            log.info("Importação finalizada. {} produtos processados.", produtosSalvos);

        } catch (Exception e) {
            log.error("Erro fatal na importação", e);
            throw new RuntimeException("Erro ao processar arquivo: " + e.getMessage());
        }
    }
}