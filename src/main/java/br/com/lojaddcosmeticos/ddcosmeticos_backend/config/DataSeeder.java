package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

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
                    "admin",
                    "admin@dd.com",
                    passwordEncoder.encode("123456"),
                    PerfilDoUsuario.ROLE_ADMIN
            );
            usuarioRepository.save(admin);
            log.info("✅ Usuário Admin criado: admin / 123456");
        }
    }

    private void carregarProdutosDoCSV() {
        // Removemos a verificação de count() > 0 para permitir correções incrementais,
        // mas agora verificaremos produto por produto.

        try {
            log.info("📦 Iniciando importação de produtos.csv...");

            ClassPathResource resource = new ClassPathResource("produtos.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

            String line;
            boolean header = true;
            int count = 0;
            int pulados = 0;

            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }

                String[] colunas = line.split(";", -1);
                if (colunas.length < 5) continue;

                // 1. TRATAMENTO DO CÓDIGO DE BARRAS (Corrige erro "7,22E+11")
                String codigoBarras = tratarCodigoBarras(colunas[0]);

                // Se o código for inválido ou vazio, pula
                if (codigoBarras.isEmpty() || codigoBarras.equals("0")) continue;

                // 2. VERIFICA SE JÁ EXISTE (Evita erro Unique Constraint)
                Optional<Produto> existente = produtoRepository.findByCodigoBarras(codigoBarras);
                if (existente.isPresent()) {
                    pulados++;
                    continue;
                }

                Produto p = new Produto();
                p.setCodigoBarras(codigoBarras);
                p.setDescricao(limparTexto(colunas[2]));

                // Valores
                p.setPrecoCusto(converterValor(colunas[3]));
                p.setPrecoVenda(converterValor(colunas[4]));
                p.setPrecoMedioPonderado(p.getPrecoCusto());
                p.setUnidade(limparTexto(colunas[7]));

                // Novos Campos
                if (colunas.length > 9) p.setCategoria(limparTexto(colunas[9]));
                if (colunas.length > 10) p.setSubcategoria(limparTexto(colunas[10]));
                if (colunas.length > 14) p.setMarca(limparTexto(colunas[14]));

                // Reforma Tributária (Padrão)
                p.setClassificacaoReforma(TipoTributacaoReforma.PADRAO);

                // Estoque
                Integer estoque = converterInteiro(colunas[13]);
                p.setEstoqueNaoFiscal(estoque);
                p.setEstoqueFiscal(0);
                p.atualizarSaldoTotal();
                p.setEstoqueMinimo(converterInteiro(colunas[12]));

                // Fiscais
                if (colunas.length > 20) p.setNcm(limparTexto(colunas[20]));
                if (colunas.length > 22) p.setCest(limparTexto(colunas[22]));

                // Validação final
                if (p.getDescricao() != null && !p.getDescricao().isEmpty()) {
                    produtoRepository.save(p);
                    count++;
                }
            }

            log.info("✅ Importação finalizada! {} cadastrados, {} pulados (já existiam).", count, pulados);

        } catch (Exception e) {
            log.error("❌ Erro ao importar CSV: ", e);
        }
    }

    // --- MÉTODOS DE LIMPEZA E CONVERSÃO ---

    private String tratarCodigoBarras(String bruto) {
        String limpo = limparTexto(bruto);
        if (limpo.isEmpty()) return "";

        // Se detectar notação científica (Ex: 7,89E+12)
        if (limpo.toUpperCase().contains("E+")) {
            try {
                // Troca vírgula por ponto para o Java entender o número
                limpo = limpo.replace(",", ".");
                BigDecimal bd = new BigDecimal(limpo);
                return bd.toPlainString(); // Converte para "789123456789"
            } catch (Exception e) {
                return limpo; // Se falhar, retorna o original
            }
        }
        return limpo;
    }

    private String limparTexto(String texto) {
        if (texto == null) return "";
        return texto.replace("\"", "").trim();
    }

    private BigDecimal converterValor(String valor) {
        try {
            String limpo = limparTexto(valor).replace(",", ".");
            if (limpo.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(limpo);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer converterInteiro(String valor) {
        try {
            String limpo = limparTexto(valor).replace(",", ".");
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