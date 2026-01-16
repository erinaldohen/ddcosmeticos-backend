package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    // Removemos o ProdutoService pois a importação agora é via Frontend
    public DataSeeder(UsuarioRepository usuarioRepository,
                      PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Apenas cria os usuários iniciais
        carregarUsuarioAdmin();
        carregarUsuarioOperador();

        log.info("🚀 DataSeeder finalizado. Produtos devem ser importados via interface web.");
    }

    private void carregarUsuarioAdmin() {
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
        if (usuarioRepository.findByMatriculaOrEmail("caixa", "caixa@lojaddcosmeticos.com.br").isEmpty()) {
            Usuario operador = new Usuario();
            operador.setNome("Operador de Caixa");
            operador.setMatricula("caixa");
            operador.setEmail("caixa@lojaddcosmeticos.com.br");
            operador.setSenha(passwordEncoder.encode("123456"));
            operador.setPerfilDoUsuario(PerfilDoUsuario.ROLE_USUARIO);
            operador.setAtivo(true);

            usuarioRepository.save(operador);
            log.info("✅ Usuário 'caixa' criado com sucesso!");
        } else {
            log.info("ℹ️ Usuário 'caixa' já existe.");
        }
    }
}