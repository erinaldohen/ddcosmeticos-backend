// Local: src/main/java/br/com/lojaddcosmeticos/ddcosmeticos_backend/DdcosmeticosBackendApplication.java (Alteração)

package br.com.lojaddcosmeticos.ddcosmeticos_backend;

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
     * Componente executado após a inicialização do Spring, apenas no perfil 'dev'.
     * Responsável pela importação inicial do catálogo de produtos do CSV.
     * @param produtoService O serviço de produto injetado.
     * @return Um CommandLineRunner que executa a importação.
     */
    @Profile("dev")
    @Bean
    public CommandLineRunner initData(ProdutoService produtoService) {
        return args -> {
            System.out.println("--- PERFIL DEV ATIVO. INICIANDO IMPORTAÇÃO DE PRODUTOS ---");

            // O nome do arquivo CSV deve estar correto e no diretório raiz do projeto (ou no classpath)
            int count = produtoService.importarProdutosCSV("Produtos (1).csv");

            if (count > 0) {
                System.out.println("--- IMPORTAÇÃO CONCLUÍDA. Total de produtos importados: " + count + " ---");
            } else {
                System.out.println("--- FALHA NA IMPORTAÇÃO. Verifique o caminho/formato do CSV. ---");
            }
        };
    }
}