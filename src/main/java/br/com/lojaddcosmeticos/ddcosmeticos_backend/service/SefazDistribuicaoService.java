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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@Service
public class SefazDistribuicaoService {

    @Autowired
    private NotaPendenteImportacaoRepository notaPendenteRepository;

    @Autowired
    private ControleSefazNsuRepository nsuRepository;

    @Transactional
    public void buscarNovasNotasNaSefaz(ConfiguracoesNfe config, String cnpjEmpresa) throws Exception {

        ControleSefazNsu controleNsu = nsuRepository.findByCnpjEmpresa(cnpjEmpresa)
                .orElse(new ControleSefazNsu(cnpjEmpresa, "0"));

        int lotesProcessados = 0;
        boolean temMaisNotas = true;

        while (temMaisNotas && lotesProcessados < 50) {
            String ultimoNsu = controleNsu.getUltimoNsu();
            String ultimoNsuFormatado = String.format("%015d", Long.parseLong(ultimoNsu));

            RetDistDFeInt retorno = Nfe.distribuicaoDfe(
                    config, PessoaEnum.JURIDICA, cnpjEmpresa, ConsultaDFeEnum.NSU, ultimoNsuFormatado
            );

            System.out.println("🚀 [SEFAZ] NSU Pesquisado: " + ultimoNsuFormatado + " | Status: " + retorno.getCStat() + " (" + retorno.getXMotivo() + ")");

            if ("138".equals(retorno.getCStat()) || "137".equals(retorno.getCStat())) {

                // Atualiza o ponteiro de onde parámos
                controleNsu.setUltimoNsu(retorno.getUltNSU());
                nsuRepository.save(controleNsu);

                // Descompacta os documentos encontrados
                if (retorno.getLoteDistDFeInt() != null) {
                    for (RetDistDFeInt.LoteDistDFeInt.DocZip docZip : retorno.getLoteDistDFeInt().getDocZip()) {
                        String schema = docZip.getSchema();
                        String xmlDescompactado = descompactarGZip(docZip.getValue());
                        String nsuDoDocumento = docZip.getNSU();

                        if (schema.startsWith("resNFe")) {
                            salvarNotaPendente(xmlDescompactado, nsuDoDocumento, "PENDENTE_MANIFESTACAO");
                        } else if (schema.startsWith("procNFe")) {
                            salvarNotaPendente(xmlDescompactado, nsuDoDocumento, "PRONTO_IMPORTACAO");
                        }
                    }
                }

                long ultNsuAtual = Long.parseLong(retorno.getUltNSU());
                long maxNsuSefaz = Long.parseLong(retorno.getMaxNSU());
                long distancia = maxNsuSefaz - ultNsuAtual;

                System.out.println("📊 [RADAR NSU] Posição Atual: " + ultNsuAtual + " | Teto SEFAZ: " + maxNsuSefaz + " | Faltam: " + distancia + " eventos.");

                if (ultNsuAtual >= maxNsuSefaz) {
                    System.out.println("✅ Fila da SEFAZ 100% Esvaziada e Atualizada!");
                    temMaisNotas = false;
                }

            } else {
                System.out.println("🛑 Resposta não esperada ou Consumo Indevido. Pausando busca.");
                temMaisNotas = false;
            }

            lotesProcessados++;

            if (temMaisNotas) {
                Thread.sleep(1000);
            }
        }
    }

    // =========================================================
    // MÉTODOS AUXILIARES CORRIGIDOS
    // =========================================================

    private void salvarNotaPendente(String xml, String nsu, String status) {
        String chaveAcesso = extrairComRegex(xml, "chNFe");

        if (chaveAcesso == null || notaPendenteRepository.existsByChaveAcesso(chaveAcesso)) {
            System.out.println("⚠️ Nota ignorada. Chave nula ou já existe: " + chaveAcesso);
            return;
        }

        NotaPendenteImportacao nota = new NotaPendenteImportacao();
        nota.setChaveAcesso(chaveAcesso);
        nota.setNsu(nsu);
        nota.setXmlCompleto(xml);
        nota.setStatus(status);

        // Data de captura atual
        nota.setDataCaptura(LocalDateTime.now());

        // Extrações robustas (Tolerantes a Resumo ou Completa)
        String nomeFornecedor = extrairComRegex(xml, "xNome");
        if (nomeFornecedor == null) nomeFornecedor = "Fornecedor da Chave " + chaveAcesso.substring(0, 14); // Fallback

        String cnpjFornecedor = extrairComRegex(xml, "CNPJ");
        if (cnpjFornecedor == null) cnpjFornecedor = extrairComRegex(xml, "CPF"); // Pode ser produtor rural

        // Extrai a data e o valor (crucial para o frontend não quebrar)
        String dhEmi = extrairComRegex(xml, "dhEmi");
        if (dhEmi != null && dhEmi.length() >= 19) {
            try {
                // Tenta fazer o parse básico (Ex: 2026-04-20T10:30:00-03:00)
                nota.setDataEmissao(LocalDateTime.parse(dhEmi.substring(0, 19)));
            } catch (Exception e) {}
        }

        String vNF = extrairComRegex(xml, "vNF");
        if (vNF != null) {
            try {
                nota.setValorTotal(new java.math.BigDecimal(vNF));
            } catch (Exception e) {}
        }

        nota.setNomeFornecedor(nomeFornecedor);
        nota.setCnpjFornecedor(cnpjFornecedor);

        notaPendenteRepository.save(nota);
        System.out.println("💾 Nova nota salva no Banco! Chave: " + chaveAcesso);
    }

    /**
     * 🔥 EXTRATOR À PROVA DE BALA (REGEX)
     * Procura a tag independentemente de namespaces ou quebras de linha.
     */
    private String extrairComRegex(String xml, String tagName) {
        // Regex: procura por <tagName> ou <ns:tagName> e extrai o valor até fechar a tag
        Pattern pattern = Pattern.compile("<(?:\\w+:)?(" + tagName + ")[^>]*>(.*?)</(?:\\w+:)?\\1>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(2).trim(); // O conteúdo está no grupo 2
        }
        return null;
    }

    private String descompactarGZip(byte[] bytesCompactados) throws Exception {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytesCompactados);
             GZIPInputStream gis = new GZIPInputStream(bis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) > 0) { bos.write(buffer, 0, len); }
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    @Transactional
    public String buscarNotaPorChaveEspecifca(ConfiguracoesNfe config, String cnpjEmpresa, String chaveAcesso) throws Exception {
        chaveAcesso = chaveAcesso.replaceAll("\\D", "");

        if (chaveAcesso.length() != 44) {
            throw new RuntimeException("A chave de acesso deve ter exatamente 44 números.");
        }

        RetDistDFeInt retorno = Nfe.distribuicaoDfe(
                config, PessoaEnum.JURIDICA, cnpjEmpresa, ConsultaDFeEnum.CHAVE, chaveAcesso
        );

        if ("138".equals(retorno.getCStat()) && retorno.getLoteDistDFeInt() != null) {
            for (RetDistDFeInt.LoteDistDFeInt.DocZip docZip : retorno.getLoteDistDFeInt().getDocZip()) {
                String xml = descompactarGZip(docZip.getValue());
                salvarNotaPendente(xml, docZip.getNSU(), "PRONTO_IMPORTACAO");
            }
            return "Nota " + chaveAcesso + " localizada e descarregada com sucesso!";
        }

        throw new RuntimeException("A SEFAZ respondeu: " + retorno.getXMotivo());
    }
}