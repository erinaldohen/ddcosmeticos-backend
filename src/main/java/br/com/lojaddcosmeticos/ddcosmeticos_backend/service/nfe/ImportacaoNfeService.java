package br.com.lojaddcosmeticos.ddcosmeticos_backend.service.nfe;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.FormaDePagamento;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Fornecedor;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.FornecedorRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.EstoqueService;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.ProdutoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
public class ImportacaoNfeService {

    @Autowired private ProdutoService produtoService;
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
        String chaveAcesso = getTagValue(doc.getDocumentElement(), "chNFe");
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
        String cest = getTagValue(prod, "CEST"); // Pode ser null
        String cfop = getTagValue(prod, "CFOP");

        // Extração de CST/CSOSN (Pode variar dependendo do regime do fornecedor)
        String cst = extrairCstOuCsosn(imposto);

        // Valores
        BigDecimal quantidade = new BigDecimal(getTagValue(prod, "qCom"));
        BigDecimal valorUnitario = new BigDecimal(getTagValue(prod, "vUnCom"));

        // Se não tiver EAN válido, tenta usar o código interno do fornecedor ou gera erro
        if (ean == null || ean.isEmpty() || ean.equals("SEM GTIN")) {
            // Lógica opcional: Ignorar ou cadastrar com código interno prefixado
            log.warn("Produto sem EAN ignorado: {}", nomeProd);
            return;
        }

        // --- CORREÇÃO 1: Busca e Atualização com novos campos ---
        Produto produto = produtoRepository.findByCodigoBarras(ean)
                .orElse(new Produto());

        if (produto.getId() == null) {
            // Produto Novo
            produto.setCodigoBarras(ean);
            produto.setDescricao(nomeProd.toUpperCase()); // CORREÇÃO: setDescricao em vez de setNome
            produto.setAtivo(true);
            produto.setUnidade(getTagValue(prod, "uCom"));
        }

        // Atualiza dados fiscais sempre (para garantir inteligência fiscal)
        if (ncm != null && !ncm.isEmpty()) produto.setNcm(ncm);
        if (cest != null && !cest.isEmpty()) produto.setCest(cest);
        if (cst != null && !cst.isEmpty()) produto.setCst(cst);

        // Define Monofásico baseado no NCM (usando a lógica do Service se quiser, ou manual aqui)
        // Como o produtoService já tem a lógica no 'salvar/atualizar', podemos deixar o repositório salvar direto
        // mas é bom garantir o booleano monofásico se soubermos.
        // Vou deixar o service recalcular se necessário numa futura atualização via tela.

        produtoRepository.save(produto);

        // --- CORREÇÃO 2: Registro de Estoque ---
        // Prepara DTO para o EstoqueService
        EstoqueRequestDTO entrada = new EstoqueRequestDTO();
        entrada.setCodigoBarras(ean);
        entrada.setQuantidade(quantidade);
        entrada.setPrecoCusto(valorUnitario);
        entrada.setNumeroNotaFiscal(numeroNota);
        entrada.setFornecedorCnpj(fornecedor.getCpfOuCnpj());

        // Define padrão para entrada automática via XML (pode ajustar conforme regra)
        entrada.setFormaPagamento(FormaDePagamento.BOLETO);
        entrada.setQuantidadeParcelas(1);
        entrada.setDataVencimentoBoleto(LocalDate.now().plusDays(28));

        estoqueService.registrarEntrada(entrada);
    }

    private Fornecedor processarFornecedor(Element emit) {
        String cnpj = getTagValue(emit, "CNPJ");
        String nome = getTagValue(emit, "xNome");
        String fantasia = getTagValue(emit, "xFant");

        return fornecedorRepository.findByCpfOuCnpj(cnpj)
                .orElseGet(() -> {
                    Fornecedor novo = new Fornecedor();
                    novo.setCpfOuCnpj(cnpj);
                    novo.setRazaoSocial(nome);
                    novo.setNomeFantasia(fantasia != null ? fantasia : nome);
                    novo.setAtivo(true);
                    novo.setTipoPessoa("JURIDICA");
                    return fornecedorRepository.save(novo);
                });
    }

    private String extrairCstOuCsosn(Element imposto) {
        if (imposto == null) return null;

        // Tenta encontrar ICMS
        NodeList icmsGroup = imposto.getElementsByTagName("ICMS");
        if (icmsGroup.getLength() > 0) {
            Element icms = (Element) icmsGroup.item(0);
            // Itera sobre os filhos (ICMS00, ICMS10, ICMSSN102, etc)
            NodeList childs = icms.getChildNodes();
            for(int i=0; i<childs.getLength(); i++) {
                if (childs.item(i) instanceof Element) {
                    Element tipoIcms = (Element) childs.item(i);
                    // Tenta CST
                    String cst = getTagValue(tipoIcms, "CST");
                    if (cst != null) return cst;
                    // Tenta CSOSN
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