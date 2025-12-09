// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/DdcosmeticosBackendApplication.java

package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Usuario;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.UsuarioRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class DdcosmeticosBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DdcosmeticosBackendApplication.class, args);
    }

    /**
     * Componente executado após a inicialização, apenas no perfil 'dev'.
     * Responsável pela importação inicial do catálogo de produtos e criação de usuários.
     */
    @Profile("dev")
    @Bean
    // NOVO: Adiciona UsuarioRepository à lista de argumentos para injeção
    public CommandLineRunner initData(ProdutoService produtoService, UsuarioRepository usuarioRepository) {
        return args -> {
            System.out.println("--- PERFIL DEV ATIVO. INICIANDO CONFIGURAÇÕES INICIAIS ---");

            // 1. Importação de Produtos
            int count = produtoService.importarProdutosCSV("Produtos (1).csv");
            if (count > 0) {
                System.out.println("--- IMPORTAÇÃO CONCLUÍDA. Total de produtos importados: " + count + " ---");
            } else {
                System.out.println("--- FALHA NA IMPORTAÇÃO. Verifique o caminho/formato do CSV. ---");
            }

            // 2. Criação de Usuário Padrão para Testes (AUDITORIA)
            if (usuarioRepository.findByMatricula("CAIXA01").isEmpty()) {
                usuarioRepository.save(new Usuario("Operador Caixa 01", "CAIXA01"));
                usuarioRepository.save(new Usuario("Gerente Supervisão", "GERENTE02"));
                System.out.println("--- USUÁRIOS INICIAIS CRIADOS (CAIXA01, GERENTE02). ---");
            } else {
                System.out.println("--- USUÁRIOS INICIAIS JÁ EXISTEM. PULANDO CRIAÇÃO. ---");
            }

        };
    }
}