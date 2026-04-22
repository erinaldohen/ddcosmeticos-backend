package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ControleSefazNsu;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.NotaPendenteImportacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ControleSefazNsuRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.NotaPendenteImportacaoRepository;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.ConsultaDFeEnum;
import br.com.swconsultoria.nfe.dom.enuns.PessoaEnum;
import br.com.swconsultoria.nfe.schema.distdfeint.DistDFeInt;
import br.com.swconsultoria.nfe.schema.retdistdfeint.RetDistDFeInt;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@Service
public class SefazDistribuicaoService {

    @Autowired
    private NotaPendenteImportacaoRepository notaPendenteRepository;

    @Autowired
    private ControleSefazNsuRepository nsuRepository;

    @Transactional
    public void buscarNovasNotasNaSefaz(ConfiguracoesNfe config, String cnpjEmpresa) throws Exception {

        // 1. Busca o último NSU na base de dados (se não existir, começa do 0)
        ControleSefazNsu controleNsu = nsuRepository.findByCnpjEmpresa(cnpjEmpresa)
                .orElse(new ControleSefazNsu(cnpjEmpresa, "0"));

        String ultimoNsu = controleNsu.getUltimoNsu();

        // 2. Prepara a requisição para a SEFAZ
        DistDFeInt distDFeInt = new DistDFeInt();
        distDFeInt.setVersao("1.01");
        distDFeInt.setTpAmb(config.getAmbiente().getCodigo());
        distDFeInt.setCUFAutor(config.getEstado().getCodigoUF());
        distDFeInt.setCNPJ(cnpjEmpresa);

        DistDFeInt.DistNSU distNSU = new DistDFeInt.DistNSU();
        String ultimoNsuFormatado = String.format("%015d", Long.parseLong(ultimoNsu));
        distNSU.setUltNSU(ultimoNsuFormatado);
        distDFeInt.setDistNSU(distNSU);

        // 3. Executa a busca na SEFAZ
        RetDistDFeInt retorno = Nfe.distribuicaoDfe(
                config,
                PessoaEnum.JURIDICA,  // Tipo de Pessoa
                cnpjEmpresa,          // CNPJ da DD Cosméticos
                ConsultaDFeEnum.NSU,  // Vamos pesquisar a partir do último NSU
                ultimoNsuFormatado    // O número com os 15 zeros à esquerda
        );

        // 4. Processa os documentos encontrados
        if (retorno.getLoteDistDFeInt() != null) {
            for (RetDistDFeInt.LoteDistDFeInt.DocZip docZip : retorno.getLoteDistDFeInt().getDocZip()) {
                String schema = docZip.getSchema();

                // Descompacta os bytes zipados da SEFAZ para uma String legível (XML)
                String xmlDescompactado = descompactarGZip(docZip.getValue());
                String nsuDoDocumento = docZip.getNSU();

                if (schema.startsWith("resNFe")) {
                    salvarNotaPendente(xmlDescompactado, nsuDoDocumento, "PENDENTE_MANIFESTACAO");
                } else if (schema.startsWith("procNFe")) {
                    salvarNotaPendente(xmlDescompactado, nsuDoDocumento, "PRONTO_IMPORTACAO");
                }
            }

            // 5. Salva o Último NSU devolvido pela SEFAZ para a próxima pesquisa
            controleNsu.setUltimoNsu(retorno.getUltNSU());
            nsuRepository.save(controleNsu);
        }
    }

    // =========================================================
    // MÉTODOS AUXILIARES CORRIGIDOS
    // =========================================================

    private void salvarNotaPendente(String xml, String nsu, String status) {
        String chaveAcesso = extrairTagXml(xml, "chNFe");

        // Se a chave não for encontrada ou a nota já estiver no banco, ignoramos
        if (chaveAcesso == null || notaPendenteRepository.existsByChaveAcesso(chaveAcesso)) {
            return;
        }

        NotaPendenteImportacao nota = new NotaPendenteImportacao();
        nota.setChaveAcesso(chaveAcesso);
        nota.setNsu(nsu);
        nota.setXmlCompleto(xml);
        nota.setStatus(status);

        // Tenta extrair o nome e CNPJ do fornecedor do XML (Tags <xNome> e <CNPJ>)
        nota.setNomeFornecedor(extrairTagXml(xml, "xNome"));
        nota.setCnpjFornecedor(extrairTagXml(xml, "CNPJ"));

        notaPendenteRepository.save(nota);
    }

    // Extrator rápido de tags usando String (mais leve que instanciar um DocumentBuilder para cada nota)
    private String extrairTagXml(String xml, String tag) {
        try {
            String tagAbertura = "<" + tag + ">";
            String tagFecho = "</" + tag + ">";
            if (xml.contains(tagAbertura) && xml.contains(tagFecho)) {
                return xml.split(tagAbertura)[1].split(tagFecho)[0];
            }
        } catch (Exception e) {
            // Retorna null se não conseguir ler
        }
        return null;
    }

    // Descompactador Nativo de GZIP em Java
    private String descompactarGZip(byte[] bytesCompactados) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytesCompactados);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }
}