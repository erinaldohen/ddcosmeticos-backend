package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.RegraTributaria;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.RegraTributariaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final ProdutoRepository produtoRepository;
    private final RegraTributariaRepository regraRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      ProdutoRepository produtoRepository,
                      RegraTributariaRepository regraRepository,
                      PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.produtoRepository = produtoRepository;
        this.regraRepository = regraRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        carregarUsuarios();
        carregarRegrasTributarias();
    }

    private void carregarUsuarios() {
        // Verifica se já existe algum usuário, se não, cria o admin
        if (usuarioRepository.count() == 0) {
            log.info("👤 [DataSeeder] Criando usuário ADMIN padrão...");
            Usuario admin = new Usuario();
            admin.setNome("Administrador");
            admin.setMatricula("admin");
            admin.setSenha(passwordEncoder.encode("admin123"));
            admin.setPerfil(PerfilDoUsuario.ROLE_ADMIN);
            usuarioRepository.save(admin);
            log.info("✅ Usuário 'admin' criado com sucesso!");
        } else {
            log.info("ℹ️ Usuários já existem no banco. Criação pulada.");
        }
    }

    private void carregarRegrasTributarias() {
        if (regraRepository.count() == 0) {
            log.info("⚖️ [DataSeeder] Carregando regras da Reforma Tributária...");

            // FASE 1: 2026 - Teste (0.9% CBS + 0.1% IBS)
            regraRepository.save(new RegraTributaria(2026,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    "0.0010", "0.0090", "1.0000"));

            // FASE 2: 2027 - Extinção PIS/COFINS
            regraRepository.save(new RegraTributaria(2027,
                    LocalDate.of(2027, 1, 1), LocalDate.of(2028, 12, 31),
                    "0.0010", "0.0900", "1.0000"));

            // FASE 3: 2029 - Transição Escalonada
            regraRepository.save(new RegraTributaria(2029,
                    LocalDate.of(2029, 1, 1), LocalDate.of(2029, 12, 31),
                    "0.0200", "0.0900", "0.9000"));

            regraRepository.save(new RegraTributaria(2033,
                    LocalDate.of(2033, 1, 1), LocalDate.of(2099, 12, 31),
                    "0.1700", "0.0900", "0.0000"));
        }
    }

    // Mantido método stub para compatibilidade se houver chamadas antigas
    public String importarProdutos() {
        return "Importação via API habilitada.";
    }
}