// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/DdcosmeticosBackendApplication.java (REVISÃO DE SEGURANÇA)

package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder; // NOVO IMPORT

@SpringBootApplication
public class DdcosmeticosBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DdcosmeticosBackendApplication.class, args);
    }

    @Profile("dev")
    @Bean
    // NOVO: Adiciona PasswordEncoder
    public CommandLineRunner initData(ProdutoService produtoService, UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            System.out.println("--- PERFIL DEV ATIVO. INICIANDO CONFIGURAÇÕES INICIAIS ---");

            // 1. Importação de Produtos
            int count = produtoService.importarProdutosCSV("Produtos (1).csv");
            if (count > 0) {
                System.out.println("--- IMPORTAÇÃO CONCLUÍDA. Total de produtos importados: " + count + " ---");
            }

            // 2. Criação de Usuário Padrão para Testes (AUDITORIA E LOGIN)
            if (usuarioRepository.findByMatricula("CAIXA01").isEmpty()) {

                // Senhas: '123456' e 'admin'
                String senhaCaixa = passwordEncoder.encode("123456");
                String senhaGerente = passwordEncoder.encode("admin");

                usuarioRepository.save(new Usuario("Operador Caixa 01", "CAIXA01", senhaCaixa, "ROLE_CAIXA"));
                usuarioRepository.save(new Usuario("Gerente Supervisão", "GERENTE02", senhaGerente, "ROLE_GERENTE"));
                System.out.println("--- USUÁRIOS DE SEGURANÇA CRIADOS (CAIXA01, GERENTE02). Senhas criptografadas. ---");
            } else {
                System.out.println("--- USUÁRIOS INICIAIS JÁ EXISTEM. PULANDO CRIAÇÃO. ---");
            }

        };
    }
}