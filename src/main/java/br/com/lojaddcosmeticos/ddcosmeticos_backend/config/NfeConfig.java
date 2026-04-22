package br.com.lojaddcosmeticos.ddcosmeticos_backend.config;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.certificado.exception.CertificadoException;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.exception.NfeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NfeConfig {

    @Autowired
    private ConfiguracaoLojaRepository configuracaoLojaRepository;

    public ConfiguracoesNfe construirConfiguracaoDinamica(boolean forcarProducao) throws NfeException, CertificadoException {

        ConfiguracaoLoja configLoja = configuracaoLojaRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Configuração da loja não encontrada na base de dados."));

        AmbienteEnum ambiente = forcarProducao ? AmbienteEnum.PRODUCAO :
                (configLoja.isProducao() ? AmbienteEnum.PRODUCAO : AmbienteEnum.HOMOLOGACAO);

        byte[] bytesCertificado = configLoja.getFiscal().getArquivoCertificado();
        String senhaCertificado = configLoja.getFiscal().getSenhaCert();

        if (bytesCertificado == null || bytesCertificado.length == 0) {
            throw new RuntimeException("O Certificado Digital não está cadastrado na base de dados!");
        }

        Certificado certificado = CertificadoService.certificadoPfxBytes(bytesCertificado, senhaCertificado);

        return ConfiguracoesNfe.criarConfiguracoes(
                EstadosEnum.PE,
                ambiente,
                certificado,
                "schemas"
        );
    }
}