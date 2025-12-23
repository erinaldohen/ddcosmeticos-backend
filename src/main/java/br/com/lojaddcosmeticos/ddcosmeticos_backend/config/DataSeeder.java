package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.PerfilDoUsuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev") // Só roda em ambiente de desenvolvimento
public class DataSeeder implements CommandLineRunner {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {

        String matriculaAdmin = "admin01";

        // Verifica se já existe para não duplicar
        if (usuarioRepository.findByMatricula(matriculaAdmin).isEmpty()) {

            Usuario admin = new Usuario(
                    "Administrador DD", // Nome
                    matriculaAdmin,     // Matrícula
                    passwordEncoder.encode("123456"), // Senha Criptografada
                    PerfilDoUsuario.ROLE_ADMIN // Perfil
            );

            // O campo 'ativo' já nasce true na classe Usuario

            usuarioRepository.save(admin);

            System.out.println("----------------------------------------------");
            System.out.println(">>> CARGA INICIAL: ADMIN CRIADO COM SUCESSO");
            System.out.println(">>> Login (Matrícula): " + matriculaAdmin);
            System.out.println(">>> Senha: 123456");
            System.out.println("----------------------------------------------");
        }
    }
}