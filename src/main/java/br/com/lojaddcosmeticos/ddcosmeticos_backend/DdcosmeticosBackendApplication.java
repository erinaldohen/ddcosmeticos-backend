package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal da aplicação.
 * Em produção, esta clujasse deve ser a mais limpa possível.
 */
@SpringBootApplication
public class DdcosmeticosBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DdcosmeticosBackendApplication.class, args);
        System.out.println("--- SISTEMA INICIADO COM SUCESSO EM AMBIENTE DE PRODUÇÃO ---");
    }
}