package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ImportacaoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImportacaoService importacaoService;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      PasswordEncoder passwordEncoder,
                      ImportacaoService importacaoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.importacaoService = importacaoService;
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
        try {
            ClassPathResource resource = new ClassPathResource("produtos.csv");
            if (!resource.exists()) {
                return; // Arquivo não existe, segue a vida
            }

            log.info("📦 Iniciando carga inicial de produtos...");

            // AGORA PASSAMOS O INPUTSTREAM DIRETO, SEM MOCKMULTIPARTFILE
            try (InputStream inputStream = resource.getInputStream()) {
                importacaoService.importarViaInputStream(inputStream);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao carregar produtos.csv na inicialização: ", e);
        }
    }
}