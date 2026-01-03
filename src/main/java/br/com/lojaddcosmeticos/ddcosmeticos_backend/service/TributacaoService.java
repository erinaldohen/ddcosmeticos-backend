package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class TributacaoService {

    private static final Map<String, String> TABELA_CEST_COSMETICOS = new HashMap<>();

    static {
        // Mapeamento direto de NCM para CEST (Exemplos reais de cosméticos)
        TABELA_CEST_COSMETICOS.put("33041000", "20.010.00"); // Batom
        TABELA_CEST_COSMETICOS.put("33042010", "20.011.00"); // Sombra
        TABELA_CEST_COSMETICOS.put("34011190", "20.022.00"); // Sabonetes de toucador
    }

    @Transactional
    public void classificarProduto(Produto produto) {
        if (produto.getNcm() == null || produto.getNcm().isBlank()) {
            return;
        }

        String ncmLimpo = produto.getNcm().replaceAll("[^0-9]", "");

        // 1. Inteligência de CEST (Busca por NCM exato)
        if (produto.getCest() == null || produto.getCest().isBlank()) {
            if (TABELA_CEST_COSMETICOS.containsKey(ncmLimpo)) {
                produto.setCest(TABELA_CEST_COSMETICOS.get(ncmLimpo));
            }
        }

        // 2. Regra de Monofásico (PIS/COFINS) - Lei 10.147/2000
        // Em vez de um Map gigante, verificamos o prefixo do NCM
        // Produtos das posições 3303 a 3307 são quase todos monofásicos
        boolean ehMonofasico = ncmLimpo.startsWith("3303") ||
                ncmLimpo.startsWith("3304") ||
                ncmLimpo.startsWith("3305") ||
                ncmLimpo.startsWith("3307") ||
                ncmLimpo.startsWith("3401");

        // Aqui você deve usar o campo correto da sua entidade Produto
        // Se sua entidade usa o Enum StatusFiscal:
        // produto.setStatusFiscal(ehMonofasico ? StatusFiscal.MONOFASICO : StatusFiscal.TRIBUTADO);

        log.info("Produto {} classificado: NCM {}, Monofásico: {}",
                produto.getDescricao(), ncmLimpo, ehMonofasico);
    }
}