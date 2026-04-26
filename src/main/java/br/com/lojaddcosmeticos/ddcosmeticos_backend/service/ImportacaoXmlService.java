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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImportacaoXmlService {

    @Autowired
    private MovimentoEstoqueRepository movimentoEstoqueRepository;

    @Autowired
    private FornecedorRepository fornecedorRepository;

    @Autowired
    private ProdutoService produtoService; // 🔥 Injetado o Validador GS1

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
    }

    @Transactional
    public RetornoImportacaoXmlDTO simularImportacaoXmlString(String xmlCompleto) {

        RetornoImportacaoXmlDTO dto = new RetornoImportacaoXmlDTO();

        // 1. Localiza a tag raiz do Emitente no XML
        String emitenteBloco = extrairTagXml(xmlCompleto, "emit");
        String cnpjFornecedor = null;
        String razaoSocial = null;
        String ieForn = "ISENTO";
        String emailForn = "";
        String foneForn = "";
        String cepForn = "";
        String logradouroForn = "DADOS CAPTURADOS VIA XML";
        String numeroForn = "S/N";
        String bairroForn = "";
        String cidadeForn = "";
        String ufForn = "";

        if (emitenteBloco != null) {
            cnpjFornecedor = extrairTagXml(emitenteBloco, "CNPJ");
            if (cnpjFornecedor == null) cnpjFornecedor = extrairTagXml(emitenteBloco, "CPF");
            razaoSocial = extrairTagXml(emitenteBloco, "xNome");
            String ieExtract = extrairTagXml(emitenteBloco, "IE");
            if (ieExtract != null && !ieExtract.trim().isEmpty()) ieForn = ieExtract;
            String enderEmit = extrairTagXml(emitenteBloco, "enderEmit");
            if (enderEmit != null) {
                foneForn = extrairTagXml(enderEmit, "fone"); cepForn = extrairTagXml(enderEmit, "CEP"); logradouroForn = extrairTagXml(enderEmit, "xLgr"); numeroForn = extrairTagXml(enderEmit, "nro"); bairroForn = extrairTagXml(enderEmit, "xBairro"); cidadeForn = extrairTagXml(enderEmit, "xMun"); ufForn = extrairTagXml(enderEmit, "UF");
            }
            emailForn = extrairTagXml(emitenteBloco, "email");
            if (emailForn == null && enderEmit != null) emailForn = extrairTagXml(enderEmit, "email");
        } else {
            cnpjFornecedor = extrairTagXml(xmlCompleto, "CNPJ"); razaoSocial = extrairTagXml(xmlCompleto, "xNome");
        }

        foneForn = (foneForn != null) ? foneForn : ""; emailForn = (emailForn != null) ? emailForn : ""; cepForn = (cepForn != null) ? cepForn : ""; logradouroForn = (logradouroForn != null) ? logradouroForn : "DADOS CAPTURADOS VIA XML"; numeroForn = (numeroForn != null) ? numeroForn : "S/N"; bairroForn = (bairroForn != null) ? bairroForn : ""; cidadeForn = (cidadeForn != null) ? cidadeForn : ""; ufForn = (ufForn != null) ? ufForn : "";

        Long fornecedorIdFinal = null;

        if (cnpjFornecedor != null && !cnpjFornecedor.trim().isEmpty()) {
            Optional<Fornecedor> fornOpt = fornecedorRepository.findByCnpj(cnpjFornecedor);
            if (fornOpt.isPresent()) {
                fornecedorIdFinal = fornOpt.get().getId(); razaoSocial = fornOpt.get().getRazaoSocial();
            } else {
                Fornecedor novoForn = new Fornecedor();
                novoForn.setCnpj(cnpjFornecedor); novoForn.setRazaoSocial(razaoSocial != null ? razaoSocial : "FORNECEDOR " + cnpjFornecedor); novoForn.setNomeFantasia(razaoSocial != null ? razaoSocial : "FORNECEDOR " + cnpjFornecedor); novoForn.setInscricaoEstadual(ieForn); novoForn.setEmail(emailForn); novoForn.setTelefone(foneForn); novoForn.setCep(cepForn); novoForn.setLogradouro(logradouroForn); novoForn.setNumero(numeroForn); novoForn.setBairro(bairroForn); novoForn.setCidade(cidadeForn); novoForn.setUf(ufForn); novoForn.setAtivo(true);
                novoForn = fornecedorRepository.save(novoForn); fornecedorIdFinal = novoForn.getId();
            }
        }

        dto.setFornecedorId(fornecedorIdFinal); dto.setCnpjFornecedor(cnpjFornecedor); dto.setRazaoSocialFornecedor(razaoSocial != null ? razaoSocial : "Fornecedor Desconhecido");

        String numNota = extrairTagXml(xmlCompleto, "nNF"); String dataEmissao = extrairTagXml(xmlCompleto, "dhEmi");
        dto.setNumeroNota(numNota != null ? numNota : "S/N"); dto.setDataEmissao(dataEmissao);

        List<ItemXmlDTO> itensList = new ArrayList<>();
        Pattern detPattern = Pattern.compile("<det\\b[^>]*>(.*?)</det>", Pattern.DOTALL); Matcher detMatcher = detPattern.matcher(xmlCompleto);

        while (detMatcher.find()) {
            String blocoDet = detMatcher.group(1);
            ItemXmlDTO itemDto = new ItemXmlDTO();

            itemDto.setCodigoNoFornecedor(extrairTagXml(blocoDet, "cProd"));

            String cEAN = extrairTagXml(blocoDet, "cEAN");
            if (cEAN != null && (cEAN.equalsIgnoreCase("SEM GTIN") || cEAN.trim().isEmpty())) {
                cEAN = "";
            } else {
                // 🔥 GATILHO DA BARREIRA DE ENTRADA: O EAN do XML é filtrado antes de ir para a tela!
                cEAN = produtoService.auditarECorrigirEanGs1(cEAN);
            }

            itemDto.setCodigoBarras(cEAN);
            itemDto.setDescricao(extrairTagXml(blocoDet, "xProd"));
            itemDto.setNcm(extrairTagXml(blocoDet, "NCM"));
            itemDto.setUnidade(extrairTagXml(blocoDet, "uCom"));

            String qCom = extrairTagXml(blocoDet, "qCom"); if (qCom != null) itemDto.setQuantidade(new BigDecimal(qCom));
            String vUnCom = extrairTagXml(blocoDet, "vUnCom"); if (vUnCom != null) itemDto.setPrecoCusto(new BigDecimal(vUnCom));
            String vProd = extrairTagXml(blocoDet, "vProd"); if (vProd != null) itemDto.setTotal(new BigDecimal(vProd));

            itensList.add(itemDto);
        }

        dto.setItensXml(itensList);
        return dto;
    }

    private String extrairTagXml(String xml, String tag) {
        Pattern pattern = Pattern.compile("<(?:\\w+:)?(" + tag + ")[^>]*>(.*?)</(?:\\w+:)?\\1>", Pattern.DOTALL); Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) { return matcher.group(2).trim(); } return null;
    }
}