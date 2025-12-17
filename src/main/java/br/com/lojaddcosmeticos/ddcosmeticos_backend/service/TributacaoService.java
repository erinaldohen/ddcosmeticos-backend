package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j; // Opcional: para logs

@Slf4j
@Service
public class TributacaoService {

    // Constantes para evitar "Magic Numbers" no código
    private static final String NCM_GENERICO_COSMETICOS = "33049990";
    private static final String ORIGEM_PADRAO = "NACIONAL";

    public void classificarProduto(Produto produto) {
        // 1. Tratamento de NCM ausente ou inválido
        String ncm = (produto.getNcm() != null) ? produto.getNcm().replaceAll("\\D", "") : "";

        if (ncm.isEmpty() || ncm.length() < 4) {
            produto.setNcm(NCM_GENERICO_COSMETICOS);
            ncm = NCM_GENERICO_COSMETICOS;
        }

        // 2. Lógica de Enquadramento Monofásico (Lei 10.147/2000)
        // Usamos prefixos conhecidos de cosméticos isentos no varejo
        boolean ehMonofasico = ncm.startsWith("3303") || // Perfumes
                ncm.startsWith("3304") || // Beleza e Maquiagem
                ncm.startsWith("3305") || // Produtos Capilares
                ncm.startsWith("3307");   // Higiene Pessoal

        produto.setMonofasico(ehMonofasico);

        // 3. Define origem se estiver nula
        if (produto.getOrigem() == null || produto.getOrigem().isEmpty()) {
            produto.setOrigem(ORIGEM_PADRAO);
        }

        // 4. Garante que possuiNfEntrada seja consistente (Regra de Negócio)
        // Se a origem é Nacional e tem NCM, assume-se potencial para NF
        if (produto.getNcm() != null && !produto.getNcm().equals(NCM_GENERICO_COSMETICOS)) {
            produto.setPossuiNfEntrada(true);
        }
    }
}