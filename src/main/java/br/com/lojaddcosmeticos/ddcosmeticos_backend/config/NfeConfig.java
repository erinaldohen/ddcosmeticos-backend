package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class NfeConfig {
    /* * 🚀 OTIMIZAÇÃO FISCAL:
     * A configuração do Certificado e do SWConsultoria foi movida inteiramente
     * para dentro do NfceService.java.
     * * Porquê? Para garantir "Hot-Swap". Assim, quando o administrador fizer
     * o upload de um novo certificado PFX pelo Frontend, o sistema não
     * precisará ser reiniciado. A próxima nota fiscal a ser emitida já vai
     * consultar o banco de dados, pegar os bytes do certificado atualizado
     * e assinar a nota corretamente em tempo real.
     */
}