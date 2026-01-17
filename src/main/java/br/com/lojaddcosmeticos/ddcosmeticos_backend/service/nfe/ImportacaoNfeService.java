package br.com.lojaddcosmeticos.ddcosmeticos_backend.service.nfe;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
public class ImportacaoNfeService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private EstoqueService estoqueService;
    @Autowired private FornecedorRepository fornecedorRepository;

    @Transactional
    public void processarXmlNfe(byte[] xmlData) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlData));

        // 1. Dados da NFe
        String numeroNota = getTagValue(doc.getDocumentElement(), "nNF");

        // 2. Dados do Fornecedor
        Element emit = (Element) doc.getElementsByTagName("emit").item(0);
        Fornecedor fornecedor = processarFornecedor(emit);

        // 3. Itens da Nota
        NodeList dets = doc.getElementsByTagName("det");

        for (int i = 0; i < dets.getLength(); i++) {
            Element det = (Element) dets.item(i);
            processarItem(det, fornecedor, numeroNota);
        }

        log.info("Importação de NFe concluída. Nota: {}", numeroNota);
    }

    private void processarItem(Element det, Fornecedor fornecedor, String numeroNota) {
        Element prod = (Element) det.getElementsByTagName("prod").item(0);
        Element imposto = (Element) det.getElementsByTagName("imposto").item(0);

        // Dados Básicos
        String ean = getTagValue(prod, "cEAN");
        String nomeProd = getTagValue(prod, "xProd");
        String ncm = getTagValue(prod, "NCM");
        String cest = getTagValue(prod, "CEST");
        String cst = extrairCstOuCsosn(imposto);

        // Valores
        BigDecimal quantidade = new BigDecimal(getTagValue(prod, "qCom"));
        BigDecimal valorUnitario = new BigDecimal(getTagValue(prod, "vUnCom"));

        if (ean == null || ean.isEmpty() || ean.equals("SEM GTIN")) {
            log.warn("Produto sem EAN ignorado (Importação XML): {}", nomeProd);
            return;
        }

        // Busca ou Cria Produto
        Produto produto = produtoRepository.findByCodigoBarras(ean)
                .orElse(new Produto());

        if (produto.getId() == null) {
            produto.setCodigoBarras(ean);
            produto.setDescricao(nomeProd != null ? nomeProd.toUpperCase() : "PRODUTO SEM NOME");
            produto.setAtivo(true);
            produto.setUnidade(getTagValue(prod, "uCom"));
            produto.setOrigem("0"); // Default Nacional
            produto.setEstoqueFiscal(0);
            produto.setEstoqueNaoFiscal(0);
            produto.setQuantidadeEmEstoque(0);
        }

        if (ncm != null && !ncm.isEmpty()) produto.setNcm(ncm);
        if (cest != null && !cest.isEmpty()) produto.setCest(cest);
        if (cst != null && !cst.isEmpty()) produto.setCst(cst);

        produtoRepository.save(produto);

        // Registro de Estoque
        EstoqueRequestDTO entrada = new EstoqueRequestDTO();
        entrada.setCodigoBarras(ean);
        entrada.setQuantidade(quantidade);
        entrada.setPrecoCusto(valorUnitario);
        entrada.setNumeroNotaFiscal(numeroNota);

        // CORREÇÃO: Usa getCnpj() conforme entidade Fornecedor
        entrada.setFornecedorCnpj(fornecedor.getCnpj());

        // Dados Financeiros Padrão para Entrada Automática
        entrada.setFormaPagamento(FormaDePagamento.BOLETO);
        entrada.setQuantidadeParcelas(1);
        entrada.setDataVencimentoBoleto(LocalDate.now().plusDays(28));

        estoqueService.registrarEntrada(entrada);
    }

    private Fornecedor processarFornecedor(Element emit) {
        String cnpj = getTagValue(emit, "CNPJ");
        String nome = getTagValue(emit, "xNome");
        String fantasia = getTagValue(emit, "xFant");

        // Extrai endereço básico para evitar erro de cadastro incompleto
        Element enderEmit = (Element) emit.getElementsByTagName("enderEmit").item(0);
        String logradouro = getTagValue(enderEmit, "xLgr");
        String numero = getTagValue(enderEmit, "nro");
        String bairro = getTagValue(enderEmit, "xBairro");
        String municipio = getTagValue(enderEmit, "xMun");
        String uf = getTagValue(enderEmit, "UF");
        String cep = getTagValue(enderEmit, "CEP");

        return fornecedorRepository.findByCnpj(cnpj)
                .orElseGet(() -> {
                    Fornecedor novo = new Fornecedor();
                    novo.setCnpj(cnpj);
                    novo.setRazaoSocial(nome != null ? nome.toUpperCase() : "FORNECEDOR XML");
                    novo.setNomeFantasia(fantasia != null ? fantasia.toUpperCase() : novo.getRazaoSocial());
                    novo.setAtivo(true);

                    // Preenche endereço
                    novo.setLogradouro(logradouro);
                    novo.setNumero(numero);
                    novo.setBairro(bairro);
                    novo.setCidade(municipio);
                    novo.setUf(uf);
                    novo.setCep(cep);

                    return fornecedorRepository.save(novo);
                });
    }

    private String extrairCstOuCsosn(Element imposto) {
        if (imposto == null) return null;
        NodeList icmsGroup = imposto.getElementsByTagName("ICMS");
        if (icmsGroup.getLength() > 0) {
            Node icmsNode = icmsGroup.item(0);
            NodeList childs = icmsNode.getChildNodes();
            for(int i=0; i<childs.getLength(); i++) {
                if (childs.item(i) instanceof Element) {
                    Element tipoIcms = (Element) childs.item(i);
                    String cst = getTagValue(tipoIcms, "CST");
                    if (cst != null) return cst;
                    String csosn = getTagValue(tipoIcms, "CSOSN");
                    if (csosn != null) return csosn;
                }
            }
        }
        return null;
    }

    private String getTagValue(Element element, String tagName) {
        if (element == null) return null;
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList != null && nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return null;
    }
}