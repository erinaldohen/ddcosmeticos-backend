package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // CORREÇÃO: Adicionado "dashboard" à lista de caches permitidos
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("produtos", "configuracoes", "dashboard");

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES) // Cache expira em 10 min
                .maximumSize(5000)); // Máximo de 5 mil itens na memória
        return cacheManager;
    }
}