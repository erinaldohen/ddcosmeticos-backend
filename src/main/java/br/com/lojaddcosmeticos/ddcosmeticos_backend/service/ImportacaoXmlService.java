package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.RegraNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportacaoXmlService {

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    /**
     * Este método processa o XML que o Robô da SEFAZ já baixou e guardou no banco.
     */
    @Transactional
    public void processarImportacaoXmlString(String xmlCompleto) {

        // 1. Extrai a Chave de Acesso do XML
        String chaveExtraidaDoXml = extrairTagXml(xmlCompleto, "chNFe");

        if (chaveExtraidaDoXml == null || chaveExtraidaDoXml.trim().isEmpty()) {
            throw new RegraNegocioException("Arquivo XML inválido: Chave de acesso não encontrada.");
        }

        // =========================================================
        // 🔥 VALIDAÇÃO DE DUPLICIDADE (A NOSSA REGRA DE OURO) 🔥
        // =========================================================
        if (movimentoEstoqueRepository.existsByChaveAcesso(chaveExtraidaDoXml)) {
            throw new RegraNegocioException("Esta nota (Chave: " + chaveExtraidaDoXml + ") já foi importada no sistema anteriormente!");
        }

        // =========================================================
        // 3. LÓGICA DE IMPORTAÇÃO (A implementar pela equipa)
        // =========================================================

        System.out.println("A iniciar a importação da nota: " + chaveExtraidaDoXml);

        /* TODO para a equipa Java:
         - Converter a String 'xmlCompleto' usando JAXB ou Jackson XML
         - Procurar/Cadastrar Fornecedor
         - Salvar os Movimentos de Estoque (com a chave de acesso)
         - Gerar o Contas a Pagar
         */
    }

    // Método auxiliar rápido para extrair a tag sem precisar de um parser pesado
    private String extrairTagXml(String xml, String tag) {
        try {
            String tagAbertura = "<" + tag + ">";
            String tagFecho = "</" + tag + ">";
            if (xml.contains(tagAbertura) && xml.contains(tagFecho)) {
                return xml.split(tagAbertura)[1].split(tagFecho)[0];
            }
        } catch (Exception e) {
            // Ignora e retorna null abaixo
        }
        return null;
    }
}