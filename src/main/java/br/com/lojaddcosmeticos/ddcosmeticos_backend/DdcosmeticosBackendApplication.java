package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import jakarta.annotation.PostConstruct; // Importante
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

import java.util.TimeZone;

@SpringBootApplication
@EnableCaching

public class DdcosmeticosBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DdcosmeticosBackendApplication.class, args);
    }

    // --- ADICIONE ISTO AQUI ---
    @PostConstruct
    public void init() {
        // Força o sistema a rodar no horário de Brasília/Recife, independente do servidor
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }
}