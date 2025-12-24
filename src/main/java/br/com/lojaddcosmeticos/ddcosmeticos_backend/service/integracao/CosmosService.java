package br.com.lojaddcosmeticos.ddcosmeticos_backend.services.integracao;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ProdutoExternoDTO;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class CosmosService {

    public Optional<ProdutoExternoDTO> consultarEan(String ean) {
        // SIMULAÇÃO: Se for este EAN, finge que achou na internet
        if ("7891000100100".equals(ean)) {
            return Optional.of(new ProdutoExternoDTO(
                    ean,
                    "Hidratante Nivea Soft 200ml", // Nome Oficial
                    "33049910", // NCM Correto
                    "20.026.00", // CEST Correto
                    "https://cdn.cosmos.bluesoft.com.br/img/200x200/nivea.jpg",
                    null
            ));
        }
        // Aqui entraria o RestTemplate real no futuro
        return Optional.empty();
    }
}