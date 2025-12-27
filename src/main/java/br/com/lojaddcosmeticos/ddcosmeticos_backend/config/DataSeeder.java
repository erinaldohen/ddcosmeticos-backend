package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Serviço responsável pela importação de dados.
 * NÃO implementa CommandLineRunner, portanto NÃO roda sozinho no boot.
 */
@Slf4j
@Service
public class DataSeeder {

    private final ProdutoRepository produtoRepository;

    private final List<String> NCMS_MONOFASICOS = Arrays.asList(
            "3303", "3304", "3305", "3307", "3401"
    );

    public DataSeeder(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    /**
     * MUDANÇA: Nome do método mudou de 'run' para 'importarProdutos'.
     * Retorna String para exibir no Swagger.
     */
    public String importarProdutos() {
        log.info("📂 [DataSeeder] Iniciando importação manual via API Admin...");

        ClassPathResource resource = new ClassPathResource("produtos.csv");
        if (!resource.exists()) {
            return "❌ Erro: Arquivo 'produtos.csv' não encontrado.";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            int processados = 0;
            int novos = 0;

            while ((line = reader.readLine()) != null) {
                if (header || line.trim().isEmpty()) {
                    header = false;
                    continue;
                }

                try {
                    String[] data = line.split(";");
                    if (data.length < 5) continue;

                    String ean = limparEan(data[0]);
                    if (ean.isEmpty()) continue;

                    // Lógica de Criar ou Atualizar
                    Produto p = produtoRepository.findByCodigoBarras(ean).orElse(new Produto());
                    boolean ehNovo = (p.getId() == null);

                    p.setCodigoBarras(ean);
                    if (data.length > 2) p.setDescricao(data[2]);

                    // Preços
                    if (data.length > 3) {
                        p.setPrecoCusto(limparDecimal(data[3]));
                        // Define preço médio inicial se for novo
                        if (p.getPrecoMedioPonderado() == null || p.getPrecoMedioPonderado().equals(BigDecimal.ZERO)) {
                            p.setPrecoMedioPonderado(p.getPrecoCusto());
                        }
                    }
                    if (data.length > 4) p.setPrecoVenda(limparDecimal(data[4]));

                    // Unidade
                    p.setUnidade((data.length > 7) ? data[7].trim() : "UN");

                    // Estoques (Trata o "1.000" como 1)
                    if (data.length > 12) p.setEstoqueMinimo(limparQuantidade(data[12]));
                    if (data.length > 13) p.setEstoqueNaoFiscal(limparQuantidade(data[13]));

                    p.atualizarSaldoTotal();

                    // Fiscal (NCM e CST)
                    String ncm = (data.length > 20) ? data[20].replaceAll("[^0-9]", "") : null;
                    if (ncm != null && ncm.length() > 8) ncm = ncm.substring(0, 8);
                    p.setNcm(ncm);

                    if (data.length > 22) p.setCest(data[22].replaceAll("[^0-9]", ""));

                    configurarFiscal(p, ncm);
                    p.setAtivo(true);

                    produtoRepository.save(p);
                    processados++;
                    if (ehNovo) novos++;

                } catch (Exception e) {
                    // Ignora linha com erro e continua
                }
            }

            return String.format("✅ Sucesso! %d produtos processados (%d novos).", processados, novos);

        } catch (Exception e) {
            log.error("Erro fatal na importação", e);
            return "Erro: " + e.getMessage();
        }
    }

    // --- Métodos Auxiliares ---

    private void configurarFiscal(Produto p, String ncm) {
        if (ncm != null && NCMS_MONOFASICOS.stream().anyMatch(ncm::startsWith)) {
            p.setMonofasico(true);
            p.setCst("060");
        } else {
            p.setMonofasico(false);
            p.setCst("102");
        }
    }

    private String limparEan(String s) {
        if (s == null) return "";
        return s.replaceAll("[^0-9]", "");
    }

    private BigDecimal limparDecimal(String s) {
        if (s == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace("R$", "").replace(" ", "").replace(",", ".").trim());
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Integer limparQuantidade(String s) {
        if (s == null) return 0;
        try {
            // Remove pontos de milhar antes de converter
            return new BigDecimal(s.replace(".", "").replace(",", ".").trim()).intValue();
        } catch (Exception e) { return 0; }
    }
}