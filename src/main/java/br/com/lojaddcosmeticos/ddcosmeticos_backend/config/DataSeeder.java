package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.TributacaoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private final ProdutoRepository produtoRepository;
    private final TributacaoService tributacaoService;

    @Bean
    @Profile("dev")
    public CommandLineRunner loadData() {
        return args -> {
            if (produtoRepository.count() > 0) {
                log.info("Banco de dados já contém produtos. Pulando carga inicial.");
                return;
            }

            log.info("Iniciando carga de dados do arquivo CSV...");

            ClassPathResource resource = new ClassPathResource("estoque.csv");
            if (!resource.exists()) {
                log.warn("Arquivo 'estoque.csv' não encontrado em src/main/resources. Carga ignorada.");
                return;
            }

            // Tenta ler com UTF-8. Se os acentos ficarem estranhos, mude para "ISO-8859-1"
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                boolean isHeader = true;
                int count = 0;
                String separador = ","; // Padrão inicial

                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    // --- DETECÇÃO AUTOMÁTICA DE SEPARADOR (Na primeira linha) ---
                    if (isHeader) {
                        // Se tiver mais ponto-e-vírgula que vírgula, assume que é o separador (Padrão Excel BR)
                        if (line.split(";").length > line.split(",").length) {
                            separador = ";";
                        }
                        log.info("Separador detectado: '{}'", separador);

                        // Verifica cabeçalho para debug
                        log.info("Cabeçalho lido: {}", line);
                        isHeader = false;
                        continue;
                    }

                    // Divide a linha usando o separador detectado
                    // O regex (?=...) lida com casos complexos de aspas, mas para CSV simples o split direto resolve
                    String[] colunas = separador.equals(";") ? line.split(";") : line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                    // LOG DE DEPURAÇÃO: Mostra por que a linha falhou se não tiver colunas suficientes
                    if (colunas.length < 20) {
                        log.warn("Linha ignorada (Colunas insuficientes: {} encontradas, min 20 esperadas): {}", colunas.length, line);
                        continue;
                    }

                    try {
                        // --- MAPEAMENTO (Ajustado para seu CSV) ---
                        // Índice 0: Código Barras
                        String codigoBarras = limparString(colunas[0]);

                        // Validação básica
                        if (codigoBarras.isEmpty() || codigoBarras.length() < 3) {
                            continue;
                        }

                        Produto p = new Produto();
                        p.setCodigoBarras(codigoBarras);

                        // Índice 2: Descrição
                        p.setDescricao(limparString(colunas[2]).replace("\"", ""));

                        // Índices Financeiros
                        p.setPrecoCustoInicial(parseMonetario(colunas[3]));
                        p.setPrecoVenda(parseMonetario(colunas[4]));
                        p.setPrecoMedioPonderado(p.getPrecoCustoInicial());

                        // Índice 13: Estoque
                        p.setQuantidadeEmEstoque(parseMonetario(colunas[13]));

                        // Índices Fiscais
                        // NCM está na coluna 20
                        p.setNcm(limparString(colunas[20]));

                        // CEST está na coluna 23 (se existir)
                        if (colunas.length > 23) {
                            p.setCest(limparString(colunas[23]));
                        }

                        p.setOrigem("0"); // Nacional
                        p.setAtivo(true);
                        p.setUnidade("UN");

                        // Aplica inteligência fiscal (NCM, CEST, Monofásico)
                        tributacaoService.classificarProduto(p);

                        produtoRepository.save(p);
                        count++;
                    } catch (Exception e) {
                        log.error("Erro ao processar produto '{}': {}", colunas[2], e.getMessage());
                    }
                }
                log.info("=================================================");
                log.info("Carga FINALIZADA! Total de produtos importados: {}", count);
                log.info("=================================================");
            } catch (Exception e) {
                log.error("Erro fatal na leitura do arquivo: ", e);
            }
        };
    }

    private String limparString(String s) {
        return s != null ? s.trim() : "";
    }

    private BigDecimal parseMonetario(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            // Remove R$, espaços
            String limpo = s.replace("R$", "").replace("\"", "").trim();

            // Lógica para tratar 1.200,50 vs 1,200.50
            // Se tiver vírgula e ponto, assume formato BR (1.000,00) -> remove ponto e troca vírgula por ponto
            if (limpo.contains(",") && limpo.contains(".")) {
                limpo = limpo.replace(".", "").replace(",", ".");
            }
            // Se só tiver vírgula, troca por ponto (10,50 -> 10.50)
            else if (limpo.contains(",")) {
                limpo = limpo.replace(",", ".");
            }
            // Se só tiver ponto, mantem (10.50 -> 10.50)

            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}