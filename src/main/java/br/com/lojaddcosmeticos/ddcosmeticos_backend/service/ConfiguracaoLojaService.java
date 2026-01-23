package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.AmbienteFiscal;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.LarguraPapel;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TemaSistema;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ConfiguracaoLojaService {

    @Autowired
    private ConfiguracaoLojaRepository repository;

    public ConfiguracaoLoja buscarConfiguracaoAtual() {
        return repository.findFirstByOrderByIdAsc()
                .orElseGet(this::criarConfiguracaoPadrao); // Se não existir, retorna padrão em memória (não salva ainda)
    }

    @Transactional
    public ConfiguracaoLoja salvarConfiguracao(ConfiguracaoLoja novaConfig) {
        // Garante que só existe 1 registro no banco
        ConfiguracaoLoja configExistente = repository.findFirstByOrderByIdAsc()
                .orElse(new ConfiguracaoLoja());

        // Atualiza os dados (você pode usar um Mapper aqui se preferir)
        // Dados Empresa
        configExistente.setNomeFantasia(novaConfig.getNomeFantasia());
        configExistente.setRazaoSocial(novaConfig.getRazaoSocial());
        configExistente.setCnpj(novaConfig.getCnpj());
        configExistente.setEmail(novaConfig.getEmail());
        configExistente.setTelefone(novaConfig.getTelefone());

        // Endereço
        configExistente.setCep(novaConfig.getCep());
        configExistente.setLogradouro(novaConfig.getLogradouro());
        configExistente.setNumero(novaConfig.getNumero());
        configExistente.setComplemento(novaConfig.getComplemento());
        configExistente.setBairro(novaConfig.getBairro());
        configExistente.setCidade(novaConfig.getCidade());
        configExistente.setUf(novaConfig.getUf());

        // Fiscal
        configExistente.setAmbienteFiscal(novaConfig.getAmbienteFiscal());
        configExistente.setHomologacaoToken(novaConfig.getHomologacaoToken());
        configExistente.setHomologacaoCscId(novaConfig.getHomologacaoCscId());
        configExistente.setProducaoToken(novaConfig.getProducaoToken());
        configExistente.setProducaoCscId(novaConfig.getProducaoCscId());

        // Sistema
        configExistente.setImpressaoAutomatica(novaConfig.getImpressaoAutomatica());
        configExistente.setLarguraPapel(novaConfig.getLarguraPapel());
        configExistente.setTemaSistema(novaConfig.getTemaSistema());
        configExistente.setBackupAutomatico(novaConfig.getBackupAutomatico());
        configExistente.setNotificacoesEmail(novaConfig.getNotificacoesEmail());

        // Financeiro
        configExistente.setMargemLucroAlvo(novaConfig.getMargemLucroAlvo());
        configExistente.setPercentualCustoFixo(novaConfig.getPercentualCustoFixo());
        configExistente.setPercentualImpostosVenda(novaConfig.getPercentualImpostosVenda());
        configExistente.setPercentualMaximoDescontoCaixa(novaConfig.getPercentualMaximoDescontoCaixa());
        configExistente.setPercentualMaximoDescontoGerente(novaConfig.getPercentualMaximoDescontoGerente());

        return repository.save(configExistente);
    }

    private ConfiguracaoLoja criarConfiguracaoPadrao() {
        ConfiguracaoLoja config = new ConfiguracaoLoja();
        // Define padrões para não quebrar o frontend
        config.setAmbienteFiscal(AmbienteFiscal.HOMOLOGACAO);
        config.setLarguraPapel(LarguraPapel.MM_80);
        config.setTemaSistema(TemaSistema.LIGHT);
        config.setImpressaoAutomatica(true);
        config.setNotificacoesEmail(true);
        config.setMargemLucroAlvo(new BigDecimal("30.00"));
        config.setPercentualImpostosVenda(new BigDecimal("4.00"));
        config.setPercentualCustoFixo(new BigDecimal("10.00"));
        config.setPercentualMaximoDescontoCaixa(new BigDecimal("5.00"));
        config.setPercentualMaximoDescontoGerente(new BigDecimal("20.00"));
        return config;
    }
}