package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RetornoImportacaoXmlDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RetornoImportacaoXmlDTO.ItemXmlDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.MovimentoEstoqueRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.RegraNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ImportacaoXmlService {

    @Autowired private MovimentoEstoqueRepository movimentoEstoqueRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private ProdutoService produtoService;

    // 🚨 CORREÇÃO: Parser XML Seguro contra ataques XXE (OWASP Standard)
    private Document lerXmlSeguro(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private String getTagValue(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent().trim();
        }
        return null;
    }

    @Transactional
    public void processarImportacaoXmlString(String xmlCompleto) {
        try {
            Document doc = lerXmlSeguro(xmlCompleto);
            String chaveExtraidaDoXml = getTagValue(doc.getDocumentElement(), "chNFe");

            if (chaveExtraidaDoXml == null || chaveExtraidaDoXml.trim().isEmpty()) {
                throw new RegraNegocioException("Arquivo XML inválido: Chave de acesso não encontrada.");
            }

            if (movimentoEstoqueRepository.existsByChaveAcesso(chaveExtraidaDoXml)) {
                throw new RegraNegocioException("Esta nota (Chave: " + chaveExtraidaDoXml + ") já foi importada no sistema!");
            }
        } catch (RegraNegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new RegraNegocioException("Erro ao ler o XML: Arquivo corrompido ou formato inválido.");
        }
    }

    @Transactional
    public RetornoImportacaoXmlDTO simularImportacaoXmlString(String xmlCompleto) {
        RetornoImportacaoXmlDTO dto = new RetornoImportacaoXmlDTO();

        try {
            Document doc = lerXmlSeguro(xmlCompleto);
            Element root = doc.getDocumentElement();

            NodeList emitNodes = root.getElementsByTagName("emit");
            String cnpjFornecedor = null, razaoSocial = null, ieForn = "ISENTO", emailForn = "", foneForn = "", cepForn = "", logradouroForn = "DADOS XML", numeroForn = "S/N", bairroForn = "", cidadeForn = "", ufForn = "";

            if (emitNodes.getLength() > 0) {
                Element emit = (Element) emitNodes.item(0);
                cnpjFornecedor = getTagValue(emit, "CNPJ");
                if (cnpjFornecedor == null) cnpjFornecedor = getTagValue(emit, "CPF");
                razaoSocial = getTagValue(emit, "xNome");

                String ieExtract = getTagValue(emit, "IE");
                if (ieExtract != null && !ieExtract.isEmpty()) ieForn = ieExtract;

                NodeList enderNodes = emit.getElementsByTagName("enderEmit");
                if (enderNodes.getLength() > 0) {
                    Element ender = (Element) enderNodes.item(0);
                    foneForn = getTagValue(ender, "fone"); cepForn = getTagValue(ender, "CEP");
                    logradouroForn = getTagValue(ender, "xLgr"); numeroForn = getTagValue(ender, "nro");
                    bairroForn = getTagValue(ender, "xBairro"); cidadeForn = getTagValue(ender, "xMun");
                    ufForn = getTagValue(ender, "UF");
                }
            }

            Long fornecedorIdFinal = null;
            if (cnpjFornecedor != null && !cnpjFornecedor.trim().isEmpty()) {
                Optional<Fornecedor> fornOpt = fornecedorRepository.findByCnpj(cnpjFornecedor);
                if (fornOpt.isPresent()) {
                    fornecedorIdFinal = fornOpt.get().getId(); razaoSocial = fornOpt.get().getRazaoSocial();
                } else {
                    Fornecedor novoForn = new Fornecedor();
                    novoForn.setCnpj(cnpjFornecedor); novoForn.setRazaoSocial(razaoSocial != null ? razaoSocial : "FORNECEDOR " + cnpjFornecedor); novoForn.setNomeFantasia(novoForn.getRazaoSocial()); novoForn.setInscricaoEstadual(ieForn); novoForn.setEmail(emailForn); novoForn.setTelefone(foneForn); novoForn.setCep(cepForn); novoForn.setLogradouro(logradouroForn); novoForn.setNumero(numeroForn); novoForn.setBairro(bairroForn); novoForn.setCidade(cidadeForn); novoForn.setUf(ufForn); novoForn.setAtivo(true);
                    novoForn = fornecedorRepository.save(novoForn); fornecedorIdFinal = novoForn.getId();
                }
            }

            dto.setFornecedorId(fornecedorIdFinal); dto.setCnpjFornecedor(cnpjFornecedor); dto.setRazaoSocialFornecedor(razaoSocial);

            String numNota = getTagValue(root, "nNF"); String dataEmissao = getTagValue(root, "dhEmi");
            dto.setNumeroNota(numNota != null ? numNota : "S/N"); dto.setDataEmissao(dataEmissao);

            List<ItemXmlDTO> itensList = new ArrayList<>();
            NodeList detNodes = root.getElementsByTagName("det");

            for (int i = 0; i < detNodes.getLength(); i++) {
                Element det = (Element) detNodes.item(i);
                ItemXmlDTO itemDto = new ItemXmlDTO();

                itemDto.setCodigoNoFornecedor(getTagValue(det, "cProd"));

                String cEAN = getTagValue(det, "cEAN");
                if (cEAN != null && (cEAN.equalsIgnoreCase("SEM GTIN") || cEAN.trim().isEmpty())) {
                    cEAN = "";
                } else {
                    cEAN = produtoService.auditarECorrigirEanGs1(cEAN);
                }

                itemDto.setCodigoBarras(cEAN);
                itemDto.setDescricao(getTagValue(det, "xProd"));
                itemDto.setNcm(getTagValue(det, "NCM"));
                itemDto.setUnidade(getTagValue(det, "uCom"));

                String qCom = getTagValue(det, "qCom"); if (qCom != null) itemDto.setQuantidade(new BigDecimal(qCom));
                String vUnCom = getTagValue(det, "vUnCom"); if (vUnCom != null) itemDto.setPrecoCusto(new BigDecimal(vUnCom));
                String vProd = getTagValue(det, "vProd"); if (vProd != null) itemDto.setTotal(new BigDecimal(vProd));

                itensList.add(itemDto);
            }

            dto.setItensXml(itensList);
            return dto;

        } catch (Exception e) {
            throw new RegraNegocioException("Erro crítico na simulação do XML: " + e.getMessage());
        }
    }
}