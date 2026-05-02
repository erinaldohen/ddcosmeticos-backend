package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ControleSefazNsu;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.NotaPendenteImportacao;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ControleSefazNsuRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.NotaPendenteImportacaoRepository;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.Evento;
import br.com.swconsultoria.nfe.dom.enuns.ManifestacaoEnum;
import br.com.swconsultoria.nfe.schema.envConfRecebto.TRetEnvEvento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Service
public class SefazDistribuicaoService {

    @Autowired
    private NotaPendenteImportacaoRepository notaPendenteRepository;

    @Autowired
    private ControleSefazNsuRepository nsuRepository;

    @Autowired
    private ConfiguracaoLojaRepository configuracaoLojaRepository;

    @Transactional
    public void buscarNovasNotasNaSefaz(ConfiguracoesNfe configPlaceholder, String cnpjEmpresa) throws Exception {

        // ✅ CORREÇÃO: Utilizando a nova lógica de busca global
        ConfiguracaoLoja configLoja = configuracaoLojaRepository.findFirstByOrderByIdAsc().orElseThrow();

        ControleSefazNsu controleNsu = nsuRepository.findByCnpjEmpresa(cnpjEmpresa)
                .orElse(new ControleSefazNsu(cnpjEmpresa, "0"));

        HttpClient client = criarHttpClientComCertificado(
                configLoja.getFiscal().getArquivoCertificado(),
                configLoja.getFiscal().getSenhaCert()
        );

        int lotesProcessados = 0;
        boolean temMaisNotas = true;

        System.out.println("🚀 [MOTOR NATIVO] Iniciando varredura na SEFAZ para: " + cnpjEmpresa);

        while (temMaisNotas && lotesProcessados < 50) {
            String ultimoNsuFormatado = String.format("%015d", Long.parseLong(controleNsu.getUltimoNsu()));

            String soapRequest = construirEnvelopeSOAPDistNSU(cnpjEmpresa, ultimoNsuFormatado, "1");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www1.nfe.fazenda.gov.br/NFeDistribuicaoDFe/NFeDistribuicaoDFe.asmx"))
                    .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe/nfeDistDFeInteresse\"")
                    .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            String cStat = extrairComRegex(responseBody, "cStat");
            String xMotivo = extrairComRegex(responseBody, "xMotivo");
            String maxNsu = extrairComRegex(responseBody, "maxNSU");
            String ultNsu = extrairComRegex(responseBody, "ultNSU");

            System.out.println("📦 Lote Retornado | Status: " + cStat + " - " + xMotivo);

            if ("138".equals(cStat) || "137".equals(cStat)) {

                controleNsu.setUltimoNsu(ultNsu);
                nsuRepository.save(controleNsu);

                processarDocumentosDoLote(responseBody);

                long maxNsuL = maxNsu != null ? Long.parseLong(maxNsu) : 0;
                long ultNsuL = ultNsu != null ? Long.parseLong(ultNsu) : 0;
                System.out.println("📊 Faltam: " + (maxNsuL - ultNsuL) + " eventos na SEFAZ.");

                if (ultNsuL >= maxNsuL) {
                    System.out.println("✅ Fila 100% Esvaziada!");
                    temMaisNotas = false;
                }
            } else {
                System.out.println("🛑 Parando busca. SEFAZ respondeu: " + xMotivo);
                temMaisNotas = false;
            }

            lotesProcessados++;
            if (temMaisNotas) Thread.sleep(1500);
        }
    }

    @Transactional
    public String manifestarEBaixarXmlCompleto(ConfiguracoesNfe config, String chaveAcesso) throws Exception {
        String cnpjEmpresa = config.getCertificado().getCnpjCpf();
        System.out.println("⏳ Iniciando Manifestação para a chave: " + chaveAcesso);

        Evento evento = new Evento();
        evento.setChave(chaveAcesso);
        evento.setCnpj(cnpjEmpresa);
        evento.setDataEvento(LocalDateTime.now());
        evento.setTipoManifestacao(ManifestacaoEnum.CIENCIA_DA_OPERACAO);

        br.com.swconsultoria.nfe.schema.envConfRecebto.TEnvEvento envEvento =
                br.com.swconsultoria.nfe.util.ManifestacaoUtil.montaManifestacao(evento, config);

        TRetEnvEvento retorno = Nfe.manifestacao(config, envEvento, false);

        String cStat = retorno.getRetEvento().get(0).getInfEvento().getCStat();
        String xMotivo = retorno.getRetEvento().get(0).getInfEvento().getXMotivo();

        if (!"135".equals(cStat) && !"573".equals(cStat)) {
            throw new RuntimeException("Falha na SEFAZ ao registrar Ciência: " + xMotivo);
        }

        System.out.println("✅ Ciência registrada! A aguardar 3 segundos para a SEFAZ gerar o XML...");
        Thread.sleep(3000);

        return buscarNotaPorChaveEspecifca(config, cnpjEmpresa, chaveAcesso);
    }

    @Transactional
    public String buscarNotaPorChaveEspecifca(ConfiguracoesNfe configPlaceholder, String cnpjEmpresa, String chaveAcesso) throws Exception {
        // ✅ CORREÇÃO: Utilizando a nova lógica de busca global
        ConfiguracaoLoja configLoja = configuracaoLojaRepository.findFirstByOrderByIdAsc().orElseThrow();
        chaveAcesso = chaveAcesso.replaceAll("\\D", "");

        if (chaveAcesso.length() != 44) {
            throw new RuntimeException("A chave de acesso deve ter exatamente 44 números.");
        }

        HttpClient client = criarHttpClientComCertificado(
                configLoja.getFiscal().getArquivoCertificado(),
                configLoja.getFiscal().getSenhaCert()
        );

        System.out.println("🎯 [SNIPER] Buscando chave: " + chaveAcesso);

        String soapRequest = construirEnvelopeSOAPConsChNFe(cnpjEmpresa, chaveAcesso, "1");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www1.nfe.fazenda.gov.br/NFeDistribuicaoDFe/NFeDistribuicaoDFe.asmx"))
                .header("Content-Type", "application/soap+xml; charset=utf-8; action=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe/nfeDistDFeInteresse\"")
                .POST(HttpRequest.BodyPublishers.ofString(soapRequest))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();

        String cStat = extrairComRegex(responseBody, "cStat");
        String xMotivo = extrairComRegex(responseBody, "xMotivo");

        if ("138".equals(cStat)) {
            processarDocumentosDoLote(responseBody);
            return "XML Completo descarregado com sucesso!";
        }

        throw new RuntimeException("A SEFAZ respondeu: " + xMotivo + " (Status: " + cStat + ")");
    }

    private void processarDocumentosDoLote(String responseBody) throws Exception {
        Pattern docZipPattern = Pattern.compile("<docZip[^>]*NSU=\"([0-9]+)\"[^>]*schema=\"([^\"]+)\"[^>]*>(.*?)</docZip>");
        Matcher matcher = docZipPattern.matcher(responseBody);

        while (matcher.find()) {
            String nsuDoDocumento = matcher.group(1);
            String schema = matcher.group(2);
            String base64Zip = matcher.group(3);

            byte[] zipBytes = Base64.getDecoder().decode(base64Zip);
            String xmlDescompactado = descompactarGZip(zipBytes);

            if (schema.startsWith("resNFe")) {
                salvarNotaPendente(xmlDescompactado, nsuDoDocumento, "PENDENTE_MANIFESTACAO");
            } else if (schema.startsWith("procNFe")) {
                salvarNotaPendente(xmlDescompactado, nsuDoDocumento, "PRONTO_IMPORTACAO");
            }
        }
    }

    private HttpClient criarHttpClientComCertificado(byte[] pfxBytes, String senha) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(pfxBytes), senha.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, senha.toCharArray());

        TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(kmf.getKeyManagers(), trustAll, new SecureRandom());

        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private String construirEnvelopeSOAPDistNSU(String cnpj, String nsu, String amb) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                "  <soap12:Header>\n" +
                "    <nfeCabecMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">\n" +
                "      <cUF>26</cUF>\n" +
                "      <versaoDados>1.01</versaoDados>\n" +
                "    </nfeCabecMsg>\n" +
                "  </soap12:Header>\n" +
                "  <soap12:Body>\n" +
                "    <nfeDistDFeInteresse xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">\n" +
                "      <nfeDadosMsg>\n" +
                "        <distDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">\n" +
                "          <tpAmb>" + amb + "</tpAmb>\n" +
                "          <cUFAutor>26</cUFAutor>\n" +
                "          <CNPJ>" + cnpj + "</CNPJ>\n" +
                "          <distNSU>\n" +
                "            <ultNSU>" + nsu + "</ultNSU>\n" +
                "          </distNSU>\n" +
                "        </distDFeInt>\n" +
                "      </nfeDadosMsg>\n" +
                "    </nfeDistDFeInteresse>\n" +
                "  </soap12:Body>\n" +
                "</soap12:Envelope>";
    }

    private String construirEnvelopeSOAPConsChNFe(String cnpj, String chave, String amb) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
                "  <soap12:Header>\n" +
                "    <nfeCabecMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">\n" +
                "      <cUF>26</cUF>\n" +
                "      <versaoDados>1.01</versaoDados>\n" +
                "    </nfeCabecMsg>\n" +
                "  </soap12:Header>\n" +
                "  <soap12:Body>\n" +
                "    <nfeDistDFeInteresse xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe\">\n" +
                "      <nfeDadosMsg>\n" +
                "        <distDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">\n" +
                "          <tpAmb>" + amb + "</tpAmb>\n" +
                "          <cUFAutor>26</cUFAutor>\n" +
                "          <CNPJ>" + cnpj + "</CNPJ>\n" +
                "          <consChNFe>\n" +
                "            <chNFe>" + chave + "</chNFe>\n" +
                "          </consChNFe>\n" +
                "        </distDFeInt>\n" +
                "      </nfeDadosMsg>\n" +
                "    </nfeDistDFeInteresse>\n" +
                "  </soap12:Body>\n" +
                "</soap12:Envelope>";
    }

    private void salvarNotaPendente(String xml, String nsu, String status) {
        String chaveAcesso = extrairComRegex(xml, "chNFe");

        if (chaveAcesso == null) {
            return;
        }

        NotaPendenteImportacao nota = notaPendenteRepository.findByChaveAcesso(chaveAcesso).orElse(null);

        if (nota != null && "PRONTO_IMPORTACAO".equals(nota.getStatus()) && "PENDENTE_MANIFESTACAO".equals(status)) {
            return;
        }

        if (nota == null) {
            nota = new NotaPendenteImportacao();
            nota.setChaveAcesso(chaveAcesso);
            nota.setDataCaptura(LocalDateTime.now());
        }

        nota.setNsu(nsu);
        nota.setXmlCompleto(xml);
        nota.setStatus(status);

        String nome = extrairComRegex(xml, "xNome");
        String cnpj = extrairComRegex(xml, "CNPJ");
        if (cnpj == null) cnpj = extrairComRegex(xml, "CPF");

        if (cnpj != null) {
            cnpj = cnpj.replaceAll("\\D", "");
            if (cnpj.length() > 14) cnpj = cnpj.substring(0, 14);
        }

        nota.setNomeFornecedor(nome != null ? nome : "FORNECEDOR " + chaveAcesso.substring(0, 14));
        nota.setCnpjFornecedor(cnpj != null ? cnpj : "N/D");

        String dhEmi = extrairComRegex(xml, "dhEmi");
        if (dhEmi != null && dhEmi.length() >= 19) {
            try {
                nota.setDataEmissao(LocalDateTime.parse(dhEmi.substring(0, 19)));
            } catch (Exception e) {
                nota.setDataEmissao(LocalDateTime.now());
            }
        } else {
            nota.setDataEmissao(LocalDateTime.now());
        }

        String vNF = extrairComRegex(xml, "vNF");
        if (vNF != null) {
            try {
                nota.setValorTotal(new java.math.BigDecimal(vNF));
            } catch (Exception e) {
            }
        }

        notaPendenteRepository.save(nota);
        System.out.println("💾 GRAVADO COM SUCESSO! Chave: " + chaveAcesso);
    }

    private String extrairComRegex(String xml, String tagName) {
        Pattern pattern = Pattern.compile("<(?:\\w+:)?(" + tagName + ")[^>]*>(.*?)</(?:\\w+:)?\\1>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) return matcher.group(2).trim();
        return null;
    }

    private String descompactarGZip(byte[] bytes) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) bos.write(buffer, 0, len);
            return bos.toString(StandardCharsets.UTF_8);
        }
    }
}