package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional // Garante consistência no PostgreSQL
    public void run(String... args) throws Exception {
        log.info("Sincronizando usuários iniciais com PostgreSQL...");

        carregarUsuarioAdmin();
        carregarUsuarioOperador();

        log.info("🚀 DataSeeder finalizado. Produtos devem ser importados via interface web.");
    }

    private void carregarUsuarioAdmin() {
        String email = "admin@lojaddcosmeticos.com.br";
        String matricula = "admin";

        // Verifica separadamente para evitar duplicidade em colunas UNIQUE do Postgres
        if (usuarioRepository.findByEmail(email).isEmpty() &&
                usuarioRepository.findByMatriculaOrEmail(matricula, email).isEmpty()) {

            Usuario admin = new Usuario();
            admin.setNome("Administrador");
            admin.setMatricula(matricula);
            admin.setEmail(email);
            admin.setSenha(passwordEncoder.encode("123456"));
            // Importante: PerfilDoUsuario deve mapear para ROLE_ADMIN
            admin.setPerfilDoUsuario(PerfilDoUsuario.ROLE_ADMIN);
            admin.setAtivo(true);

            usuarioRepository.save(admin);
            log.info("✅ Usuário 'admin' (ROLE_ADMIN) criado com sucesso!");
        } else {
            log.info("ℹ️ Usuário 'admin' já existe no banco PostgreSQL.");
        }
    }

    private void carregarUsuarioOperador() {
        String email = "caixa@lojaddcosmeticos.com.br";
        String matricula = "caixa";

        if (usuarioRepository.findByEmail(email).isEmpty() &&
                usuarioRepository.findByMatriculaOrEmail(matricula, email).isEmpty()) {

            Usuario operador = new Usuario();
            operador.setNome("Operador de Caixa");
            operador.setMatricula(matricula);
            operador.setEmail(email);
            operador.setSenha(passwordEncoder.encode("123456"));
            // Importante: PerfilDoUsuario deve mapear para ROLE_USUARIO ou ROLE_CAIXA
            operador.setPerfilDoUsuario(PerfilDoUsuario.ROLE_USUARIO);
            operador.setAtivo(true);

            usuarioRepository.save(operador);
            log.info("✅ Usuário 'caixa' (ROLE_USUARIO) criado com sucesso!");
        } else {
            log.info("ℹ️ Usuário 'caixa' já existe no banco PostgreSQL.");
        }
    }
}