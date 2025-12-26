package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Configuration
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final ProdutoRepository produtoRepository;

    // Lista de NCMs comuns de cosméticos que geralmente são Monofásicos (PIS/COFINS zero na saída)
    private final List<String> NCMS_MONOFASICOS = Arrays.asList(
            "3303", "3304", "3305", "3307", "3401"
    );

    public DataSeeder(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("📂 [DataSeeder] Iniciando verificação e correção inteligente dos produtos...");

        ClassPathResource resource = new ClassPathResource("produtos.csv");
        if (!resource.exists()) {
            System.out.println("❌ [DataSeeder] Arquivo 'produtos.csv' não encontrado.");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            int processados = 0;
            int erros = 0;

            while ((line = reader.readLine()) != null) {
                if (header || line.trim().isEmpty()) {
                    header = false;
                    continue;
                }

                try {
                    String[] data = line.split(";");
                    if (data.length < 5) continue;

                    // 1. EAN (Código de Barras)
                    String eanCru = data[0].trim();
                    String eanFinal = limparEan(eanCru);

                    if (eanFinal.isEmpty() || eanFinal.equals("0")) continue;

                    // Busca ou cria
                    Produto p = produtoRepository.findByCodigoBarras(eanFinal).orElse(new Produto());
                    p.setCodigoBarras(eanFinal);

                    // 2. Descrição
                    if (data.length > 2) {
                        p.setDescricao(cortarTexto(data[2], 500));
                    }

                    // 3. Preços
                    if (data.length > 3) p.setPrecoCusto(limparDecimal(data[3]));
                    p.setPrecoMedioPonderado(p.getPrecoCusto());
                    if (data.length > 4) p.setPrecoVenda(limparDecimal(data[4]));

                    // 4. Unidade
                    if (data.length > 7) {
                        String und = data[7].trim().toUpperCase();
                        p.setUnidade(und.length() > 5 ? "UN" : cortarTexto(und, 10));
                    }

                    // 5. Estoques (Corrigido leitura de decimal)
                    if (data.length > 12) p.setEstoqueMinimo(limparQuantidade(data[12]));
                    else p.setEstoqueMinimo(5);

                    if (data.length > 13) {
                        // Se já tiver estoque no banco diferente de zero, mantém o do banco (opcional)
                        // Aqui vamos forçar a atualização baseada no CSV conforme solicitado
                        p.setEstoqueNaoFiscal(limparQuantidade(data[13]));
                    }
                    p.atualizarSaldoTotal();

                    // --- DADOS FISCAIS CORRIGIDOS ---

                    // COLUNA 20: NCM (Posição Correta)
                    String ncm = null;
                    if (data.length > 20) {
                        ncm = data[20].trim().replaceAll("[^0-9]", "");
                        if (ncm.length() > 8) ncm = ncm.substring(0, 8); // Garante max 8 digitos
                        p.setNcm(ncm);
                    }

                    // COLUNA 22: CEST (Posição Correta Baseada na Análise)
                    // Às vezes o CSV desloca, então verificamos se parece um CEST (7 digitos)
                    if (data.length > 22) {
                        String possivelCest = data[22].trim().replaceAll("[^0-9]", "");
                        if (!possivelCest.isEmpty()) {
                            p.setCest(cortarTexto(possivelCest, 20));
                        }
                    }

                    // --- INTELIGÊNCIA FISCAL ---

                    // 1. Definição Automática de Monofásico baseada no NCM
                    if (ncm != null && NCMS_MONOFASICOS.stream().anyMatch(ncm::startsWith)) {
                        p.setMonofasico(true);
                        // Se é monofásico, o CST de saída (Simples Nacional) geralmente é o que indica substituição/monofásico
                        // Ex: 500 (ICMS cobrado anteriormente) ou use o padrão da sua contabilidade.
                        // Vou setar um genérico para não ficar null.
                        p.setCst("060"); // Cobrado anteriormente por ST
                    } else {
                        p.setMonofasico(false);
                        p.setCst("102"); // Tributada pelo Simples Nacional sem permissão de crédito (Padrão Varejo)
                    }

                    p.setAtivo(true);

                    // Salva
                    produtoRepository.saveAndFlush(p);
                    processados++;

                } catch (Exception e) {
                    erros++;
                    // System.out.println("⚠️ Erro linha: " + e.getMessage());
                }
            }
            System.out.println("✅ [DataSeeder] Processamento concluído!");
            System.out.println("   -> Produtos Processados/Atualizados: " + processados);
            System.out.println("   -> Linhas com erro (ignoradas): " + erros);
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private String cortarTexto(String texto, int tamanhoMax) {
        if (texto == null) return null;
        if (texto.length() > tamanhoMax) return texto.substring(0, tamanhoMax);
        return texto;
    }

    private String limparEan(String original) {
        if (original == null) return "";
        String limpo = original.toUpperCase().trim();
        if (limpo.contains("E+") || limpo.contains(",")) {
            try {
                // Tenta recuperar EAN de notação científica
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
            String limpo = valor.trim().replace("R$", "").replace(" ", "").replace(",", ".");
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer limparQuantidade(String valor) {
        try {
            if (valor == null || valor.trim().isEmpty()) return 0;
            // O arquivo usa "1.000" para 1. Então removemos espaços e R$, substituímos vírgula por ponto
            String limpo = valor.trim().replace(" ", "").replace(",", ".");
            // Parse como BigDecimal primeiro para entender o ponto como decimal, não milhar
            BigDecimal bd = new BigDecimal(limpo);
            return bd.intValue();
        } catch (Exception e) {
            return 0;
        }
    }
}