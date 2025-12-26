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
 * Serviço responsável pela importação e correção de dados legados (CSV).
 * Agora funciona sob demanda, acionado via API Admin.
 */
@Slf4j
@Service
public class DataSeeder {

    private final ProdutoRepository produtoRepository;

    // Lista de NCMs de cosméticos que geralmente são Monofásicos (PIS/COFINS zero na saída)
    private final List<String> NCMS_MONOFASICOS = Arrays.asList(
            "3303", "3304", "3305", "3307", "3401"
    );

    public DataSeeder(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    /**
     * Método público chamado pelo AdminController para disparar a importação.
     * Retorna um resumo do processamento.
     */
    public String importarProdutos() {
        log.info("📂 [DataSeeder] Iniciando importação manual do arquivo produtos.csv...");

        ClassPathResource resource = new ClassPathResource("produtos.csv");
        if (!resource.exists()) {
            String msg = "❌ [DataSeeder] Arquivo 'produtos.csv' não encontrado na pasta resources.";
            log.error(msg);
            return msg;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            int processados = 0;
            int novos = 0;
            int atualizados = 0;
            int erros = 0;

            while ((line = reader.readLine()) != null) {
                // Ignora cabeçalho e linhas vazias
                if (header || line.trim().isEmpty()) {
                    header = false;
                    continue;
                }

                try {
                    String[] data = line.split(";");
                    // Verifica se tem o mínimo de colunas para processar (EAN, Nome, Preço...)
                    if (data.length < 5) continue;

                    // 1. TRATAMENTO DO EAN (Código de Barras)
                    String eanCru = data[0].trim();
                    String eanFinal = limparEan(eanCru);

                    if (eanFinal.isEmpty() || eanFinal.equals("0")) continue;

                    // 2. BUSCA INTELIGENTE (Atualiza se existe, Cria se não)
                    Produto p = produtoRepository.findByCodigoBarras(eanFinal).orElse(new Produto());
                    boolean ehNovo = (p.getId() == null);

                    p.setCodigoBarras(eanFinal);

                    // 3. DESCRIÇÃO (Coluna 2)
                    if (data.length > 2) {
                        p.setDescricao(cortarTexto(data[2], 500));
                    }

                    // 4. PREÇOS (Colunas 3 e 4)
                    if (data.length > 3) p.setPrecoCusto(limparDecimal(data[3]));

                    // Define preço médio inicial igual ao custo se não tiver histórico
                    if (p.getPrecoMedioPonderado() == null || p.getPrecoMedioPonderado().compareTo(BigDecimal.ZERO) == 0) {
                        p.setPrecoMedioPonderado(p.getPrecoCusto());
                    }

                    if (data.length > 4) p.setPrecoVenda(limparDecimal(data[4]));

                    // 5. UNIDADE (Coluna 7)
                    if (data.length > 7) {
                        String und = data[7].trim().toUpperCase();
                        p.setUnidade(und.length() > 5 ? "UN" : cortarTexto(und, 10));
                    } else {
                        p.setUnidade("UN");
                    }

                    // 6. ESTOQUES (Colunas 12 e 13)
                    // Correção Crítica: "1.000" no CSV significa 1, não mil.
                    if (data.length > 12) p.setEstoqueMinimo(limparQuantidade(data[12]));
                    else p.setEstoqueMinimo(5);

                    if (data.length > 13) {
                        // Força a atualização do estoque não fiscal com o dado do CSV
                        p.setEstoqueNaoFiscal(limparQuantidade(data[13]));
                    }
                    p.atualizarSaldoTotal();

                    // --- DADOS FISCAIS & INTELIGÊNCIA ---

                    // NCM (Coluna 20)
                    String ncm = null;
                    if (data.length > 20) {
                        ncm = data[20].trim().replaceAll("[^0-9]", "");
                        if (ncm.length() > 8) ncm = ncm.substring(0, 8);
                        p.setNcm(ncm);
                    }

                    // CEST (Coluna 22)
                    if (data.length > 22) {
                        String cest = data[22].trim().replaceAll("[^0-9]", "");
                        if (!cest.isEmpty()) {
                            p.setCest(cortarTexto(cest, 20));
                        }
                    }

                    // Definição Automática de Tributação (CST e Monofásico)
                    aplicarInteligenciaFiscal(p, ncm);

                    p.setAtivo(true);

                    // Salva no banco
                    produtoRepository.saveAndFlush(p);

                    processados++;
                    if (ehNovo) novos++; else atualizados++;

                } catch (Exception e) {
                    erros++;
                    // Loga erro sem parar o processo
                    // log.warn("⚠️ Erro ao importar linha: {}", e.getMessage());
                }
            }

            String resumo = String.format("✅ Importação concluída! Processados: %d (Novos: %d, Atualizados: %d) | Falhas: %d",
                    processados, novos, atualizados, erros);
            log.info(resumo);
            return resumo;

        } catch (Exception e) {
            log.error("Erro fatal ao ler arquivo CSV", e);
            return "Erro fatal na importação: " + e.getMessage();
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private void aplicarInteligenciaFiscal(Produto p, String ncm) {
        if (ncm != null && NCMS_MONOFASICOS.stream().anyMatch(ncm::startsWith)) {
            p.setMonofasico(true);
            // CST 060: ICMS cobrado anteriormente por substituição tributária
            p.setCst("060");
        } else {
            p.setMonofasico(false);
            // CST 102: Tributada pelo Simples Nacional sem permissão de crédito
            p.setCst("102");
        }
    }

    private String cortarTexto(String texto, int tamanhoMax) {
        if (texto == null) return null;
        String limpo = texto.trim();
        return (limpo.length() > tamanhoMax) ? limpo.substring(0, tamanhoMax) : limpo;
    }

    private String limparEan(String original) {
        if (original == null) return "";
        String limpo = original.toUpperCase().trim();
        // Trata notação científica do Excel (Ex: "7,89E+12")
        if (limpo.contains("E+") || limpo.contains(",")) {
            try {
                BigDecimal bd = new BigDecimal(limpo.replace(",", ".").replace(" ", ""));
                return bd.toPlainString();
            } catch (Exception e) {
                return limpo.replaceAll("[^0-9]", "");
            }
        }
        return limpo.replaceAll("[^0-9]", "");
    }

    private BigDecimal limparDecimal(String valor) {
        try {
            if (valor == null || valor.trim().isEmpty()) return BigDecimal.ZERO;
            // Remove R$, espaços e troca vírgula por ponto
            String limpo = valor.trim().replace("R$", "").replace(" ", "").replace(",", ".");
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer limparQuantidade(String valor) {
        try {
            if (valor == null || valor.trim().isEmpty()) return 0;
            // O arquivo usa "1.000" para 1 unidade.
            // Removemos espaços e tratamos como BigDecimal para respeitar o ponto decimal.
            String limpo = valor.trim().replace(" ", "").replace(",", ".");
            return new BigDecimal(limpo).intValue();
        } catch (Exception e) {
            return 0;
        }
    }
}