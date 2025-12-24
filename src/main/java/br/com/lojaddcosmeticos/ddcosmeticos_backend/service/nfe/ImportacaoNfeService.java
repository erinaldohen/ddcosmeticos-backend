package br.com.lojaddcosmeticos.ddcosmeticos_backend.services.nfe;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoExternoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.services.integracao.CosmosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class ImportacaoNfeService {

    @Autowired
    private CosmosService cosmosService;

    // Método chamado ao ler cada linha do XML
    public ProdutoDTO processarItemXml(String ean, String nomeXml, String ncmXml, BigDecimal custo) {

        System.out.println("Processando XML - Item: " + nomeXml + " | NCM Nota: " + ncmXml);

        // 1. Consulta Inteligência Externa
        Optional<ProdutoExternoDTO> dadosReais = cosmosService.consultarEan(ean);

        String ncmFinal = ncmXml;
        String cestFinal = null;
        String imagem = null;

        if (dadosReais.isPresent()) {
            ProdutoExternoDTO api = dadosReais.get();

            // 2. CORREÇÃO DE NCM
            if (api.ncm() != null && !api.ncm().equals(ncmXml)) {
                System.out.println("!!! CORREÇÃO !!! NCM alterado de " + ncmXml + " para " + api.ncm());
                ncmFinal = api.ncm();
            }

            cestFinal = api.cest();
            imagem = api.urlImagem();
        }

        // Retorna o objeto corrigido para ser salvo ou exibido na tela
        return new ProdutoDTO(
                nomeXml,
                "Importado via XML",
                custo,
                BigDecimal.ZERO, // Venda a definir
                0, // Estoque a somar
                ean,
                ncmFinal,
                cestFinal,
                imagem,
                true
        );
    }
}