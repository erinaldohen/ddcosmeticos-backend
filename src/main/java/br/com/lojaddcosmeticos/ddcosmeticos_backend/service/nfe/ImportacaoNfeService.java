package br.com.lojaddcosmeticos.ddcosmeticos_backend.service.nfe;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConsultaCnpjDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.FornecedorService;
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
    @Autowired private FornecedorService fornecedorService; // Serviço oficial

    @Transactional
    public void processarXmlNfe(byte[] xmlData) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlData));

        // 1. Dados da NFe
        Element ide = (Element) doc.getElementsByTagName("ide").item(0);
        String numeroNota = getTagValue(ide, "nNF");
        if (numeroNota == null) numeroNota = getTagValue(doc.getDocumentElement(), "nNF");

        // 2. Dados do Fornecedor (Emitente)
        NodeList emitList = doc.getElementsByTagName("emit");
        if (emitList.getLength() == 0) {
            throw new IllegalArgumentException("XML inválido: Tag <emit> não encontrada.");
        }
        Element emit = (Element) emitList.item(0);

        // Processa fornecedor usando a API externa via FornecedorService
        Fornecedor fornecedor = processarFornecedor(emit);

        // 3. Itens da Nota
        NodeList dets = doc.getElementsByTagName("det");

        for (int i = 0; i < dets.getLength(); i++) {
            Element det = (Element) dets.item(i);
            processarItem(det, fornecedor, numeroNota);
        }

        log.info("Importação de NFe concluída. Nota: {}", numeroNota);
    }

    private Fornecedor processarFornecedor(Element emit) {
        // 1. Extrai APENAS o CNPJ do XML
        String documento = getTagValue(emit, "CNPJ");
        if (documento == null || documento.isEmpty()) {
            documento = getTagValue(emit, "CPF");
        }

        if (documento == null) throw new IllegalArgumentException("Fornecedor sem documento no XML.");
        String cnpjLimpo = documento.replaceAll("\\D", "");

        // 2. Extrai a IE (dado local importante)
        String inscricaoEstadual = getTagValue(emit, "IE");

        // 3. Busca ou Cria (mas não salva ainda)
        Fornecedor fornecedor = fornecedorRepository.findByCnpj(cnpjLimpo).orElse(new Fornecedor());
        fornecedor.setCnpj(cnpjLimpo);
        fornecedor.setAtivo(true);

        if (inscricaoEstadual != null && !inscricaoEstadual.isEmpty()) {
            fornecedor.setInscricaoEstadual(inscricaoEstadual);
        }

        // 4. CONSULTA API EXTERNA (Correção do nome do método aqui)
        try {
            log.info("Consultando dados do fornecedor {} na API externa...", cnpjLimpo);

            // [CORREÇÃO] O nome do método no FornecedorService é consultarDadosCnpj
            ConsultaCnpjDTO dadosApi = fornecedorService.consultarDadosCnpj(cnpjLimpo);

            if (dadosApi != null) {
                // SUCESSO: Usa os dados da API
                fornecedor.setRazaoSocial(dadosApi.getRazaoSocial());
                fornecedor.setNomeFantasia(dadosApi.getNomeFantasia() != null ? dadosApi.getNomeFantasia() : dadosApi.getRazaoSocial());

                fornecedor.setCep(dadosApi.getCep());
                fornecedor.setLogradouro(dadosApi.getLogradouro());
                fornecedor.setNumero(dadosApi.getNumero());
                fornecedor.setBairro(dadosApi.getBairro());
                fornecedor.setCidade(dadosApi.getMunicipio());
                fornecedor.setUf(dadosApi.getUf());
                fornecedor.setTelefone(dadosApi.getTelefone());
                fornecedor.setEmail(dadosApi.getEmail());
            } else {
                // Fallback XML
                usarDadosXmlComoFallback(fornecedor, emit);
            }
        } catch (Exception e) {
            log.error("Erro na consulta externa: {}", e.getMessage());
            usarDadosXmlComoFallback(fornecedor, emit);
        }

        return fornecedorRepository.save(fornecedor);
    }

    private void usarDadosXmlComoFallback(Fornecedor f, Element emit) {
        if (f.getRazaoSocial() == null) f.setRazaoSocial(getTagValue(emit, "xNome"));
        if (f.getNomeFantasia() == null) f.setNomeFantasia(getTagValue(emit, "xFant"));

        NodeList enderList = emit.getElementsByTagName("enderEmit");
        if (enderList.getLength() > 0) {
            Element e = (Element) enderList.item(0);
            if(f.getLogradouro() == null) f.setLogradouro(getTagValue(e, "xLgr"));
            if(f.getNumero() == null) f.setNumero(getTagValue(e, "nro"));
            if(f.getBairro() == null) f.setBairro(getTagValue(e, "xBairro"));
            if(f.getCidade() == null) f.setCidade(getTagValue(e, "xMun"));
            if(f.getUf() == null) f.setUf(getTagValue(e, "UF"));
            if(f.getCep() == null) f.setCep(getTagValue(e, "CEP"));
        }
    }

    private void processarItem(Element det, Fornecedor fornecedor, String numeroNota) {
        Element prod = (Element) det.getElementsByTagName("prod").item(0);
        Element imposto = (Element) det.getElementsByTagName("imposto").item(0);

        String ean = getTagValue(prod, "cEAN");
        String nomeProd = getTagValue(prod, "xProd");
        String ncm = getTagValue(prod, "NCM");
        String cest = getTagValue(prod, "CEST");
        String cst = extrairCstOuCsosn(imposto);

        String qCom = getTagValue(prod, "qCom");
        String vUnCom = getTagValue(prod, "vUnCom");

        BigDecimal quantidade = (qCom != null) ? new BigDecimal(qCom) : BigDecimal.ZERO;
        BigDecimal valorUnitario = (vUnCom != null) ? new BigDecimal(vUnCom) : BigDecimal.ZERO;

        if (ean == null || ean.trim().isEmpty() || ean.equals("SEM GTIN")) {
            log.warn("Produto sem EAN ignorado: {}", nomeProd);
            return;
        }

        Produto produto = produtoRepository.findByCodigoBarras(ean).orElse(new Produto());

        if (produto.getId() == null) {
            produto.setCodigoBarras(ean);
            produto.setDescricao(nomeProd != null ? nomeProd.toUpperCase() : "PRODUTO SEM NOME");
            produto.setAtivo(true);
            produto.setUnidade(getTagValue(prod, "uCom"));
            produto.setOrigem("0");
            produto.setEstoqueFiscal(0);
            produto.setEstoqueNaoFiscal(0);
            produto.setQuantidadeEmEstoque(0);
        }

        if (ncm != null && !ncm.isEmpty()) produto.setNcm(ncm);
        if (cest != null && !cest.isEmpty()) produto.setCest(cest);
        if (cst != null && !cst.isEmpty()) produto.setCst(cst);

        produtoRepository.save(produto);

        EstoqueRequestDTO entrada = new EstoqueRequestDTO();
        entrada.setCodigoBarras(ean);
        entrada.setQuantidade(quantidade);
        entrada.setPrecoCusto(valorUnitario);
        entrada.setNumeroNotaFiscal(numeroNota);
        entrada.setFornecedorCnpj(fornecedor.getCnpj());
        entrada.setFormaPagamento(FormaDePagamento.BOLETO);
        entrada.setQuantidadeParcelas(1);
        entrada.setDataVencimentoBoleto(LocalDate.now().plusDays(28));

        estoqueService.registrarEntrada(entrada);
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