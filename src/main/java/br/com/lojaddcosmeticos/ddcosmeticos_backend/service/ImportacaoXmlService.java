package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
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
        // 3. LÓGICA DE IMPORTAÇÃO
        // =========================================================

        System.out.println("A iniciar a importação da nota: " + chaveExtraidaDoXml);

        /* * TODO para a equipa Java:
         * 1. Converter a String 'xmlCompleto' usando JAXB ou Jackson XML
         * 2. Procurar/Cadastrar Fornecedor
         * 3. Loop nos itens do XML para salvar os Movimentos de Estoque
         */

        // 🔥 EXEMPLO DE COMO A EQUIPA DEVE GRAVAR O MOVIMENTO PARA A CHAVE APARECER NO FRONTEND:
        /*
        MovimentoEstoque movimento = new MovimentoEstoque();
        movimento.setProduto(produtoEncontradoNoXml);
        movimento.setQuantidadeMovimentada(quantidadeDoXml);
        movimento.setCustoMovimentado(valorUnitarioDoXml);
        movimento.setTipoMovimentoEstoque(TipoMovimentoEstoque.ENTRADA);
        movimento.setMotivoMovimentacaoDeEstoque(MotivoMovimentacaoDeEstoque.COMPRA_FORNECEDOR);
        movimento.setDocumentoReferencia(numeroDaNotaDoXml);

        // ---> A CORREÇÃO AQUI: Vincular a chave extraída ao movimento <---
        movimento.setChaveAcesso(chaveExtraidaDoXml);

        movimentoEstoqueRepository.save(movimento);
        */

        // 4. Gerar o Contas a Pagar
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