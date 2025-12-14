package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class TributacaoService {

    // Mapa Estático de Cosméticos Comuns (Descrição -> NCM)
    // Fonte: Tabela TIPI da Receita Federal
    private static final Map<String, String> NCM_MAP = new HashMap<>();

    static {
        // Cabelos
        NCM_MAP.put("SHAMPOO", "33051000");
        NCM_MAP.put("XAMPU", "33051000");
        NCM_MAP.put("CONDICIONADOR", "33059000");
        NCM_MAP.put("MASCARA", "33059000");
        NCM_MAP.put("CREME", "33059000"); // Creme de pentear
        NCM_MAP.put("LAQUE", "33053000");
        NCM_MAP.put("FIXADOR", "33053000");
        NCM_MAP.put("TINTURA", "33059000");

        // Perfumaria
        NCM_MAP.put("PERFUME", "33030010");
        NCM_MAP.put("COLONIA", "33030020");
        NCM_MAP.put("AGUA DE CHEIRO", "33030020");

        // Higiene
        NCM_MAP.put("SABONETE", "34011190"); // Barra
        NCM_MAP.put("SABONETE LIQ", "34012010");
        NCM_MAP.put("DESODORANTE", "33072010");
        NCM_MAP.put("ANTITRANSPIRANTE", "33072090");

        // Maquiagem
        NCM_MAP.put("BATOM", "33041000");
        NCM_MAP.put("RIMEL", "33042010");
        NCM_MAP.put("SOMBRA", "33042090");
        NCM_MAP.put("PO FACIAL", "33049100");
        NCM_MAP.put("ESMALTE", "33043000");
    }

    /**
     * Analisa o produto e preenche NCM e Flag Monofásico automaticamente.
     */
    public void classificarProduto(Produto produto) {
        if (produto.getDescricao() == null) return;

        String descUpper = produto.getDescricao().toUpperCase();
        String ncmEncontrado = "33049990"; // Padrão genérico de cosmético ("Outros")

        // 1. Busca Heurística de NCM
        for (Map.Entry<String, String> entry : NCM_MAP.entrySet()) {
            if (descUpper.contains(entry.getKey())) {
                ncmEncontrado = entry.getValue();
                break; // Achou o mais específico
            }
        }

        produto.setNcm(ncmEncontrado);

        // 2. Verifica se é Monofásico (PIS/COFINS Zero na revenda)
        // Regra: Quase todo o Capítulo 33 (Cosméticos) e 3401 (Sabonetes) é Monofásico na Lei 10.147/00
        boolean isMonofasico = ncmEncontrado.startsWith("33") || ncmEncontrado.startsWith("3401");

        produto.setMonofasico(isMonofasico);
    }
}