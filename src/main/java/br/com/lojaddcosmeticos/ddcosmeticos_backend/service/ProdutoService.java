package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ProdutoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Transactional(readOnly = true)
    public Produto buscarPorCodigoBarras(String ean) {
        return produtoRepository.findByCodigoBarras(ean)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não localizado com o EAN: " + ean));
    }

    // --- NOVO MÉTODO ---
    @Transactional(readOnly = true)
    public Page<Produto> listarTodos(Pageable pageable) {
        return produtoRepository.findAll(pageable);
    }

    @Transactional
    public void importarEstoqueCSV(MultipartFile file) {
        log.info("Iniciando importação de ficheiro: {}", file.getOriginalFilename());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = reader.readLine(); // Salta o cabeçalho
            List<Produto> lote = new ArrayList<>();
            String linha;
            int contador = 0;

            while ((linha = reader.readLine()) != null) {
                // Regex para separar por vírgula ignorando aspas
                String[] col = linha.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                if (col.length < 24) continue;

                String ean = col[0].trim();
                if (ean.isEmpty()) continue;

                Produto produto = produtoRepository.findByCodigoBarras(ean).orElse(new Produto());

                produto.setCodigoBarras(ean);
                produto.setDescricao(col[2].replace("\"", "").trim());

                BigDecimal custo = parseBigDecimal(col[3]);
                produto.setPrecoCustoInicial(custo);
                produto.setPrecoMedioPonderado(custo);
                produto.setPrecoVenda(parseBigDecimal(col[4]));
                produto.setQuantidadeEmEstoque(parseBigDecimal(col[13]));

                produto.setUnidade(col[7].isEmpty() ? "UN" : col[7].trim().toUpperCase());
                produto.setAtivo(col[8].equalsIgnoreCase("Sim"));

                // Correção no mapeamento de índices para evitar IndexOutOfBounds se o CSV variar
                if(col.length > 12) produto.setEstoqueMinimo(parseBigDecimal(col[12]));

                String ncmLimpo = col[20].replaceAll("[^0-9]", "");
                produto.setNcm(ncmLimpo);

                if(col.length > 23) produto.setCest(col[23].replaceAll("[^0-9]", ""));

                // Tratamento de segurança para coluna 22 (Origem)
                String origemTexto = col.length > 22 ? col[22].toUpperCase() : "";
                produto.setOrigem(origemTexto.contains("NACIONAL") ? "0" : "1");

                // Lógica Monofásico
                if (ncmLimpo.startsWith("3303") || ncmLimpo.startsWith("3304") ||
                        ncmLimpo.startsWith("3305") || ncmLimpo.startsWith("3307")) {
                    produto.setMonofasico(true);
                }

                lote.add(produto);
                contador++;

                if (lote.size() >= 50) {
                    produtoRepository.saveAll(lote);
                    lote.clear();
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

    private BigDecimal parseBigDecimal(String valor) {
        if (valor == null || valor.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            String limpo = valor.replace("\"", "").trim();
            // Lógica para tratar formatos numéricos
            if (limpo.contains(",") && limpo.contains(".")) {
                limpo = limpo.replace(".", "").replace(",", ".");
            } else if (limpo.contains(",")) {
                limpo = limpo.replace(",", ".");
            }
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}