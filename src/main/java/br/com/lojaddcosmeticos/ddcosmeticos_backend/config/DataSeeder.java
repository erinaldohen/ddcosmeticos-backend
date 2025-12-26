package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j; // Uso de Logger
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service; // Mudado para Service

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service // Agora é um serviço injetável, não roda sozinho
public class DataSeeder {

    private final ProdutoRepository produtoRepository;
    private final List<String> NCMS_MONOFASICOS = Arrays.asList("3303", "3304", "3305", "3307", "3401");

    public DataSeeder(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    // Método público para ser chamado via Controller
    public String importarProdutos() {
        log.info("Iniciando importação manual do arquivo produtos.csv...");

        try {
            ClassPathResource resource = new ClassPathResource("produtos.csv");
            if (!resource.exists()) return "Erro: Arquivo produtos.csv não encontrado.";

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                boolean header = true;
                int processados = 0;
                int erros = 0;

                while ((line = reader.readLine()) != null) {
                    if (header || line.trim().isEmpty()) { header = false; continue; }
                    try {
                        String[] data = line.split(";");
                        if (data.length < 5) continue;

                        // ... (MANTENHA A LÓGICA DE IMPORTAÇÃO QUE JÁ FIZEMOS AQUI) ...
                        // ... Copie o conteúdo do 'while' da versão anterior ...
                        // Apenas troque System.out por log.info/error se desejar

                        processarLinhaProduto(data); // Exemplo de refatoração para método auxiliar
                        processados++;
                    } catch (Exception e) {
                        erros++;
                    }
                }
                return String.format("Importação concluída! Processados: %d, Erros: %d", processados, erros);
            }
        } catch (Exception e) {
            log.error("Falha fatal na importação", e);
            return "Erro fatal: " + e.getMessage();
        }
    }

    // Método auxiliar para não duplicar código aqui na resposta
    private void processarLinhaProduto(String[] data) {
        // ... (Lógica de instanciar Produto, limpar dados e salvar) ...
        // Pode copiar exatamente o miolo do try/catch da versão anterior
        // Lembre-se de usar produtoRepository.saveAndFlush(p);
        // Use as funções limparEan, limparDecimal, etc.
        String eanCru = data[0].trim();
        String eanFinal = limparEan(eanCru);
        if (eanFinal.isEmpty() || eanFinal.equals("0")) return;

        Produto p = produtoRepository.findByCodigoBarras(eanFinal).orElse(new Produto());
        p.setCodigoBarras(eanFinal);

        if (data.length > 2) p.setDescricao(cortarTexto(data[2], 500));
        if (data.length > 3) p.setPrecoCusto(limparDecimal(data[3]));
        p.setPrecoMedioPonderado(p.getPrecoCusto());
        if (data.length > 4) p.setPrecoVenda(limparDecimal(data[4]));

        if (data.length > 7) {
            String und = data[7].trim().toUpperCase();
            p.setUnidade(und.length() > 5 ? "UN" : cortarTexto(und, 10));
        } else p.setUnidade("UN");

        if (data.length > 12) p.setEstoqueMinimo(limparQuantidade(data[12]));
        else p.setEstoqueMinimo(5);

        if (data.length > 13) p.setEstoqueNaoFiscal(limparQuantidade(data[13]));
        p.atualizarSaldoTotal();

        // Fiscais
        String ncm = null;
        if (data.length > 20) {
            ncm = data[20].trim().replaceAll("[^0-9]", "");
            if (ncm.length() > 8) ncm = ncm.substring(0, 8);
            p.setNcm(ncm);
        }
        if (data.length > 22) {
            String cest = data[22].trim().replaceAll("[^0-9]", "");
            if (!cest.isEmpty()) p.setCest(cortarTexto(cest, 20));
        }

        // Inteligência
        if (ncm != null && NCMS_MONOFASICOS.stream().anyMatch(ncm::startsWith)) {
            p.setMonofasico(true);
            p.setCst("060");
        } else {
            p.setMonofasico(false);
            p.setCst("102");
        }

        p.setAtivo(true);
        produtoRepository.saveAndFlush(p);
    }

    // ... (Métodos auxiliares limparEan, limparDecimal, etc. mantidos)
    private String limparEan(String original) {
        if (original == null) return "";
        String limpo = original.toUpperCase().trim();
        if (limpo.contains("E+") || limpo.contains(",")) {
            try { return new BigDecimal(limpo.replace(",", ".").replace(" ", "")).toPlainString(); }
            catch (Exception e) { return limpo.replaceAll("[^0-9]", ""); }
        }
        return limpo.replaceAll("[^0-9]", "");
    }

    private String cortarTexto(String t, int m) { return (t != null && t.length() > m) ? t.substring(0, m) : t; }

    private BigDecimal limparDecimal(String v) {
        try { return (v == null || v.isBlank()) ? BigDecimal.ZERO : new BigDecimal(v.trim().replace("R$", "").replace(" ", "").replace(",", ".")); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Integer limparQuantidade(String v) {
        try { return (v == null || v.isBlank()) ? 0 : new BigDecimal(v.trim().replace(" ", "").replace(",", ".")).intValue(); }
        catch (Exception e) { return 0; }
    }
}