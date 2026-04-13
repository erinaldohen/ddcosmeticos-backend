package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ValidacaoFiscalDTO;
import org.springframework.stereotype.Service;

@Service
public class FiscalService {

    /**
     * Motor de Inteligência Fiscal:
     * Avalia o NCM e a Descrição para inferir a tributação correta (Simples Nacional).
     */
    public ValidacaoFiscalDTO validarProduto(String descricao, String ncm) {
        ValidacaoFiscalDTO dto = new ValidacaoFiscalDTO();

        // Garante que o NCM tem pelo menos 8 dígitos se for válido
        String ncmLimpo = (ncm != null) ? ncm.replaceAll("\\D", "") : "";
        dto.setNcm(ncmLimpo.isEmpty() ? "00000000" : ncmLimpo);

        // Regras baseadas no Simples Nacional e NCM de Cosméticos (Capítulo 33 e 34)
        dto.setImpostoSeletivo(false);
        dto.setMonofasico(false);
        dto.setCst("102"); // Padrão Simples Nacional (Tributada pelo Simples sem permissão de crédito)
        dto.setCest("");

        if (ncmLimpo.startsWith("3304") || ncmLimpo.startsWith("3305") || ncmLimpo.startsWith("3401")) {
            // Cosméticos e Perfumaria têm alta incidência de Substituição Tributária (CEST)
            // e Tributação Monofásica de PIS/COFINS
            dto.setMonofasico(true);

            // Sugestões de CEST baseadas no Convênio ICMS 142/18 para cosméticos
            if (ncmLimpo.startsWith("330510")) { // Shampoos
                dto.setCest("2003800");
                dto.setCst("500"); // ICMS cobrado anteriormente por substituição tributária (CSOSN)
            } else if (ncmLimpo.startsWith("330590")) { // Condicionadores/Máscaras
                dto.setCest("2003900");
                dto.setCst("500");
            } else if (ncmLimpo.startsWith("330420")) { // Maquiagem Olhos
                dto.setCest("2000800");
                dto.setCst("500");
            } else if (ncmLimpo.startsWith("3401")) { // Sabonetes
                dto.setCest("2004600");
                dto.setCst("500");
            }
        }

        // Regra de Proteção: Se a descrição tiver palavras-chave, ajusta
        String descLower = descricao != null ? descricao.toLowerCase() : "";
        if (descLower.contains("cesta basica") || descLower.contains("isento")) {
            dto.setCst("400"); // Não tributada pelo Simples Nacional
        }

        return dto;
    }
}