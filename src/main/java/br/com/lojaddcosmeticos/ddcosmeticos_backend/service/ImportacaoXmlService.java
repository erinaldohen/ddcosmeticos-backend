package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RetornoImportacaoXmlDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RetornoImportacaoXmlDTO.ItemXmlDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.MotivoMovimentacaoDeEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoMovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.MovimentoEstoque;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.RegraNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImportacaoXmlService {

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    /**
     * Este método processa o XML que o Robô da SEFAZ já baixou e guardou no banco.
     * Originalmente projetado para importação direta sem revisão.
     */
    @Transactional
    public void processarImportacaoXmlString(String xmlCompleto) {
        String chaveExtraidaDoXml = extrairTagXml(xmlCompleto, "chNFe");

        if (chaveExtraidaDoXml == null || chaveExtraidaDoXml.trim().isEmpty()) {
            throw new RegraNegocioException("Arquivo XML inválido: Chave de acesso não encontrada.");
        }

        if (movimentoEstoqueRepository.existsByChaveAcesso(chaveExtraidaDoXml)) {
            throw new RegraNegocioException("Esta nota (Chave: " + chaveExtraidaDoXml + ") já foi importada no sistema anteriormente!");
        }

        System.out.println("A iniciar a importação da nota: " + chaveExtraidaDoXml);
        // A lógica efetiva de salvar itens e financeiro foi migrada para a Entrada Inteligente no React.
    }

    /**
     * 🔥 NOVO MÉTODO PARA A TELA DE ENTRADA INTELIGENTE 🔥
     * Lê o XML da base de dados e transforma no DTO que a tela React Entende
     */
    public RetornoImportacaoXmlDTO simularImportacaoXmlString(String xmlCompleto) {

        RetornoImportacaoXmlDTO dto = new RetornoImportacaoXmlDTO();

        // Dados do Cabeçalho
        String numNota = extrairTagXml(xmlCompleto, "nNF");
        String razaoSocial = extrairTagXml(xmlCompleto, "xNome"); // Pode pegar o primeiro que é o Emitente
        String dataEmissao = extrairTagXml(xmlCompleto, "dhEmi");

        dto.setNumeroNota(numNota != null ? numNota : "S/N");
        dto.setRazaoSocialFornecedor(razaoSocial != null ? razaoSocial : "Fornecedor Desconhecido");
        // Opcional: Se for preciso enviar a data de emissão para o DTO (caso o frontend espere)

        // Regex para apanhar os blocos <det> (Detalhes dos itens)
        List<ItemXmlDTO> itensList = new ArrayList<>();
        Pattern detPattern = Pattern.compile("<det\\b[^>]*>(.*?)</det>", Pattern.DOTALL);
        Matcher detMatcher = detPattern.matcher(xmlCompleto);

        while (detMatcher.find()) {
            String blocoDet = detMatcher.group(1);
            ItemXmlDTO itemDto = new ItemXmlDTO();

            itemDto.setCodigoNoFornecedor(extrairTagXml(blocoDet, "cProd"));

            // O código de barras pode vir em cEAN. Se vier "SEM GTIN", retornamos null ou vazio.
            String cEAN = extrairTagXml(blocoDet, "cEAN");
            if (cEAN != null && (cEAN.equalsIgnoreCase("SEM GTIN") || cEAN.trim().isEmpty())) {
                cEAN = "";
            }
            itemDto.setCodigoBarras(cEAN);

            itemDto.setDescricao(extrairTagXml(blocoDet, "xProd"));
            itemDto.setNcm(extrairTagXml(blocoDet, "NCM"));
            itemDto.setUnidade(extrairTagXml(blocoDet, "uCom"));

            String qCom = extrairTagXml(blocoDet, "qCom");
            if (qCom != null) itemDto.setQuantidade(new BigDecimal(qCom));

            String vUnCom = extrairTagXml(blocoDet, "vUnCom");
            if (vUnCom != null) itemDto.setPrecoCusto(new BigDecimal(vUnCom));

            String vProd = extrairTagXml(blocoDet, "vProd");
            if (vProd != null) itemDto.setTotal(new BigDecimal(vProd));

            itensList.add(itemDto);
        }

        dto.setItensXml(itensList);

        return dto;
    }

    // Método auxiliar rápido para extrair a tag sem precisar de um parser pesado
    private String extrairTagXml(String xml, String tag) {
        // Regex mais robusto que lida com possíveis namespaces (ex: <ns:tag> ou <tag>)
        Pattern pattern = Pattern.compile("<(?:\\w+:)?(" + tag + ")[^>]*>(.*?)</(?:\\w+:)?\\1>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(2).trim();
        }
        return null;
    }
}