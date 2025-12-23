package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TributacaoService {

    // Mapa estático de NCM -> CEST para o segmento de Cosméticos/Perfumaria
    private static final Map<String, String> TABELA_CEST_COSMETICOS = new HashMap<>();

    static {
        // PERFUMES E ÁGUAS DE COLÔNIA (3303)
        TABELA_CEST_COSMETICOS.put("33030010", "20.007.00");
        TABELA_CEST_COSMETICOS.put("33030020", "20.008.00");

        // PRODUTOS DE BELEZA / MAQUIAGEM (3304)
        TABELA_CEST_COSMETICOS.put("33041000", "20.010.00"); // Batom
        TABELA_CEST_COSMETICOS.put("33042010", "20.011.00"); // Sombra/Delineador
        TABELA_CEST_COSMETICOS.put("33043000", "20.012.00"); // Manicures/Vernizes (Esmaltes)
        TABELA_CEST_COSMETICOS.put("33049100", "20.014.00"); // Pós/Talcos
        TABELA_CEST_COSMETICOS.put("33049910", "20.015.00"); // Cremes de beleza
        TABELA_CEST_COSMETICOS.put("33049990", "20.016.00"); // Outros de beleza/protetor solar

        // PREPARAÇÕES CAPILARES (3305)
        TABELA_CEST_COSMETICOS.put("33051000", "20.018.00"); // Shampoos
        TABELA_CEST_COSMETICOS.put("33052000", "20.019.00"); // Laquês
        TABELA_CEST_COSMETICOS.put("33059000", "20.020.00"); // Outros capilares (Condicionador)

        // HIGIENE BUCAL (3306)
        TABELA_CEST_COSMETICOS.put("33061000", "20.023.00"); // Dentifrícios (Pasta)
        TABELA_CEST_COSMETICOS.put("33062000", "20.024.00"); // Fio dental

        // BARBEAR E DESODORANTES (3307)
        TABELA_CEST_COSMETICOS.put("33071000", "20.028.00"); // Pré/Pós barba
        TABELA_CEST_COSMETICOS.put("33072010", "20.029.00"); // Desodorante corporal
        TABELA_CEST_COSMETICOS.put("33072090", "20.030.00"); // Outros desodorantes

        // SABÕES (3401)
        TABELA_CEST_COSMETICOS.put("34011190", "20.034.00"); // Sabonetes em barra
        TABELA_CEST_COSMETICOS.put("34012010", "20.035.00"); // Sabonetes líquidos
        TABELA_CEST_COSMETICOS.put("34013000", "20.036.00"); // Produtos para lavar a pele
    }

    /**
     * Aplica inteligência fiscal no produto:
     * 1. Sanitiza NCM
     * 2. Infere CEST se estiver vazio
     * 3. Define se é Monofásico (PIS/COFINS Zero)
     */
    public void classificarProduto(Produto produto) {
        if (produto.getNcm() == null) return;

        // 1. Sanitização (Remove pontos e espaços)
        String ncmLimpo = produto.getNcm().replaceAll("[^0-9]", "");
        produto.setNcm(ncmLimpo);

        // 2. Inteligência de CEST (Substituição Tributária)
        // Se o produto não tem CEST ou está vazio, tentamos encontrar na tabela
        if (produto.getCest() == null || produto.getCest().isBlank()) {
            if (TABELA_CEST_COSMETICOS.containsKey(ncmLimpo)) {
                String cestSugerido = TABELA_CEST_COSMETICOS.get(ncmLimpo);
                produto.setCest(cestSugerido);
                log.info("Inteligência Fiscal: CEST {} aplicado automaticamente para NCM {} ({})",
                        cestSugerido, ncmLimpo, produto.getDescricao());
            } else if (ncmLimpo.length() >= 4) {
                // Tentativa por prefixo (ex: todo 3305.90...)
                String prefixo = ncmLimpo.substring(0, 4);
                // Lógica de fallback poderia entrar aqui
            }
        } else {
            // Se já tem CEST, apenas formata para remover pontos se necessário, ou deixa como está
            // produto.setCest(produto.getCest().replaceAll("[^0-9]", "")); // Opcional
        }

        // 3. Regra de Monofásico (Lei 10.147/2000)
        // Cosméticos nas posições 3303, 3304, 3305, 3307 e Sabonetes 3401 geralmente são monofásicos
        boolean ehCosmeticoMonofasico = ncmLimpo.startsWith("3303") ||
                ncmLimpo.startsWith("3304") ||
                ncmLimpo.startsWith("3305") ||
                ncmLimpo.startsWith("3307") ||
                ncmLimpo.startsWith("3401");

        produto.setMonofasico(ehCosmeticoMonofasico);
    }
}