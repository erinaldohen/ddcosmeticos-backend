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
        carregarUsuarioAdmin();
        carregarUsuarioOperador(); // <--- Novo método para criar o Caixa
        carregarProdutosDoCSV();
    }

    private void carregarUsuarioAdmin() {
        // Verifica se existe por matrícula ou e-mail
        if (usuarioRepository.findByMatriculaOrEmail("admin", "admin@lojaddcosmeticos.com.br").isEmpty()) {
            Usuario admin = new Usuario();
            admin.setNome("Administrador");
            admin.setMatricula("admin");
            admin.setEmail("admin@lojaddcosmeticos.com.br");
            admin.setSenha(passwordEncoder.encode("123456"));
            admin.setPerfilDoUsuario(PerfilDoUsuario.ROLE_ADMIN);
            admin.setAtivo(true);

            usuarioRepository.save(admin);
            log.info("✅ Usuário 'admin' (ROLE_ADMIN) criado com sucesso!");
        } else {
            log.info("ℹ️ Usuário 'admin' já existe.");
        }
    }

    private void carregarUsuarioOperador() {
        // Verifica se existe por matrícula 'caixa' ou e-mail
        if (usuarioRepository.findByMatriculaOrEmail("caixa", "caixa@lojaddcosmeticos.com.br").isEmpty()) {
            Usuario operador = new Usuario();
            operador.setNome("Operador de Caixa");
            operador.setMatricula("caixa");
            operador.setEmail("caixa@lojaddcosmeticos.com.br");
            operador.setSenha(passwordEncoder.encode("123456"));
            // Atenção: Certifique-se que no seu Enum PerfilDoUsuario existe ROLE_USER ou ROLE_CAIXA
            // Se o seu Enum for ROLE_FUNCIONARIO, altere aqui.
            operador.setPerfilDoUsuario(PerfilDoUsuario.ROLE_USUARIO);
            operador.setAtivo(true);

            usuarioRepository.save(operador);
            log.info("✅ Usuário 'caixa' (ROLE_USER) criado com sucesso!");
        } else {
            log.info("ℹ️ Usuário 'caixa' já existe.");
        }
    }

    private void carregarProdutosDoCSV() {
        try {
            ClassPathResource resource = new ClassPathResource("produtos.csv");
            if (!resource.exists()) {
                log.warn("⚠️ Arquivo 'produtos.csv' não encontrado em src/main/resources.");
                return;
            }

            log.info("📦 Iniciando carga inicial de produtos...");

            try (InputStream inputStream = resource.getInputStream()) {
                // Chama o serviço de importação robusto que configuramos
                importacaoService.importarViaInputStream(inputStream);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao carregar produtos.csv: ", e);
        }
    }
}