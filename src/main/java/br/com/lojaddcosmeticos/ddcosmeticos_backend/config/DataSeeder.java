package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j; // Importante para logs
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final ProdutoRepository produtoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      ProdutoRepository produtoRepository,
                      PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.produtoRepository = produtoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        carregarUsuarios();
        carregarProdutosDoCSV();
    }

    private void carregarUsuarios() {
        if (usuarioRepository.count() == 0) {
            Usuario admin = new Usuario(
                    "Administrador",
                    "admin",            // Matrícula
                    "admin@dd.com",     // E-mail
                    passwordEncoder.encode("123456"),
                    PerfilDoUsuario.ADMIN
            );
            usuarioRepository.save(admin);
            log.info("✅ Usuário Admin criado: admin / 123456");
        }
    }

    private void carregarProdutosDoCSV() {
        if (produtoRepository.count() > 0) {
            log.info("ℹ️ Produtos já carregados. Pulando importação.");
            return;
        }

        try {
            log.info("📦 Iniciando importação de produtos.csv...");

            // Lê o arquivo da pasta resources
            ClassPathResource resource = new ClassPathResource("produtos.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

            String line;
            boolean header = true;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue; // Pula a primeira linha (cabeçalho)
                }

                // Divide por ponto e vírgula, mantendo colunas vazias
                // O regex (?=(?:[^\"]*\"[^\"]*\")*[^\"]*$) garante que não quebre ; dentro de aspas
                String[] colunas = line.split(";", -1);

                if (colunas.length < 5) continue; // Linha inválida

                Produto p = new Produto();

                // Remove aspas extras das strings ("Valor" -> Valor)
                p.setCodigoBarras(limparTexto(colunas[0])); // Col 0: Código
                p.setDescricao(limparTexto(colunas[2]));    // Col 2: Descrição

                // Converte Preços (Troca vírgula por ponto se necessário)
                p.setPrecoCusto(converterValor(colunas[3]));        // Col 3: Custo
                p.setPrecoVenda(converterValor(colunas[4]));        // Col 4: Venda
                p.setPrecoMedioPonderado(p.getPrecoCusto());        // Inicializa médio igual ao custo

                p.setUnidade(limparTexto(colunas[7]));              // Col 7: Unidade (UN, KG)

                // Estoque (CSV Col 13: Qtd em Estoque)
                // Vamos colocar no estoque NÃO fiscal inicialmente
                Integer estoque = converterInteiro(colunas[13]);
                p.setEstoqueNaoFiscal(estoque);
                p.setEstoqueFiscal(0);
                p.atualizarSaldoTotal(); // Soma os estoques

                // Estoque Mínimo (Col 12)
                p.setEstoqueMinimo(converterInteiro(colunas[12]));

                // Dados Fiscais
                if (colunas.length > 20) p.setNcm(limparTexto(colunas[20])); // Col 20: NCM
                if (colunas.length > 22) p.setCest(limparTexto(colunas[22])); // Col 22: CEST

                // Validação básica para evitar erro de duplicidade ou nulo
                if (p.getCodigoBarras() != null && !p.getCodigoBarras().isEmpty() &&
                        p.getDescricao() != null && !p.getDescricao().isEmpty()) {

                    produtoRepository.save(p);
                    count++;
                }
            }

            log.info("✅ Importação concluída! {} produtos cadastrados.", count);

        } catch (Exception e) {
            log.error("❌ Erro ao importar CSV: ", e);
        }
    }

    // --- Métodos Auxiliares ---

    private String limparTexto(String texto) {
        if (texto == null) return "";
        // Remove aspas do início e fim e espaços extras
        return texto.replace("\"", "").trim();
    }

    private BigDecimal converterValor(String valor) {
        try {
            String limpo = limparTexto(valor);
            if (limpo.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer converterInteiro(String valor) {
        try {
            // Remove casas decimais se houver (ex: "8.000" -> "8")
            String limpo = limparTexto(valor);
            if (limpo.contains(".")) {
                limpo = limpo.substring(0, limpo.indexOf("."));
            }
            if (limpo.isEmpty()) return 0;
            return Integer.parseInt(limpo);
        } catch (Exception e) {
            return 0;
        }
    }
}