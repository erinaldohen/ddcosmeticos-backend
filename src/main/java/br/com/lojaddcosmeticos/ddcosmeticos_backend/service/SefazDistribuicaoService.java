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

        while (temMaisNotas && lotesProcessados < 10) {
            String ultimoNsu = controleNsu.getUltimoNsu();
            String ultimoNsuFormatado = String.format("%015d", Long.parseLong(ultimoNsu));

            RetDistDFeInt retorno = Nfe.distribuicaoDfe(
                    config, PessoaEnum.JURIDICA, cnpjEmpresa, ConsultaDFeEnum.NSU, ultimoNsuFormatado
            );

            System.out.println("🚀 [SEFAZ] NSU Pesquisado: " + ultimoNsuFormatado + " | Status: " + retorno.getCStat() + " (" + retorno.getXMotivo() + ")");

            // 🔥 SURPRESA 2: A MÁGICA DO NSU!
            // 138 = Achou notas | 137 = Não tem notas neste exato NSU, MAS o ponteiro deve andar!
            if ("138".equals(retorno.getCStat()) || "137".equals(retorno.getCStat())) {

                // Grava o novo NSU independente de ter vindo XML ou não
                controleNsu.setUltimoNsu(retorno.getUltNSU());
                nsuRepository.save(controleNsu);

                // Se houver XMLs anexados, descompacta e salva
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

                // Verifica se já encostou no teto da SEFAZ
                long ultNsuAtual = Long.parseLong(retorno.getUltNSU());
                long maxNsuSefaz = Long.parseLong(retorno.getMaxNSU());

                if (ultNsuAtual >= maxNsuSefaz) {
                    temMaisNotas = false; // Bateu no teto. Sai do loop.
                }

            } else {
                // Se der erro 656 (Consumo Indevido), paramos imediatamente para não bloquear o CNPJ
                temMaisNotas = false;
            }

            lotesProcessados++;

            // Pausa de 1 segundo entre chamadas para sermos amigáveis com o servidor do Governo
            Thread.sleep(1000);
        }
    }

    // =========================================================
    // MÉTODOS AUXILIARES
    // =========================================================

    private void salvarNotaPendente(String xml, String nsu, String status) {
        String chaveAcesso = extrairTagXml(xml, "chNFe");

        if (chaveAcesso == null || notaPendenteRepository.existsByChaveAcesso(chaveAcesso)) {
            return; // Impede duplicidade
        }

        NotaPendenteImportacao nota = new NotaPendenteImportacao();
        nota.setChaveAcesso(chaveAcesso);
        nota.setNsu(nsu);
        nota.setXmlCompleto(xml);
        nota.setStatus(status);
        nota.setNomeFornecedor(extrairTagXml(xml, "xNome"));
        nota.setCnpjFornecedor(extrairTagXml(xml, "CNPJ"));

        notaPendenteRepository.save(nota);
    }

    private String extrairTagXml(String xml, String tag) {
        try {
            String tagAbertura = "<" + tag + ">";
            String tagFecho = "</" + tag + ">";
            if (xml.contains(tagAbertura) && xml.contains(tagFecho)) {
                return xml.split(tagAbertura)[1].split(tagFecho)[0];
            }
        } catch (Exception e) { }
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
    // 🔥 A NOVA ROTA: Busca direta (Sniper) ignorando a fila
    @Transactional
    public String buscarNotaPorChaveEspecifca(ConfiguracoesNfe config, String cnpjEmpresa, String chaveAcesso) throws Exception {

        // Remove espaços ou pontuações que o utilizador possa ter colado
        chaveAcesso = chaveAcesso.replaceAll("\\D", "");

        if (chaveAcesso.length() != 44) {
            throw new RuntimeException("A chave de acesso deve ter exatamente 44 números.");
        }

        // Faz o pedido EXATO para essa chave
        RetDistDFeInt retorno = Nfe.distribuicaoDfe(
                config, PessoaEnum.JURIDICA, cnpjEmpresa, ConsultaDFeEnum.CHAVE, chaveAcesso
        );

        // 138 = Documento Localizado
        if ("138".equals(retorno.getCStat()) && retorno.getLoteDistDFeInt() != null) {
            for (RetDistDFeInt.LoteDistDFeInt.DocZip docZip : retorno.getLoteDistDFeInt().getDocZip()) {
                String xml = descompactarGZip(docZip.getValue());
                // Força o status para PRONTO, pois fomos buscar a nota na íntegra
                salvarNotaPendente(xml, docZip.getNSU(), "PRONTO_IMPORTACAO");
            }
            return "Nota " + chaveAcesso + " localizada e descarregada com sucesso!";
        }

        // Se a SEFAZ recusar, atira o motivo real (ex: "Nota não existe", "Apenas Resumo Disponível")
        throw new RuntimeException("A SEFAZ respondeu: " + retorno.getXMotivo());
    }
}