package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ConfiguracaoLojaService {

    @Autowired
    private ConfiguracaoLojaRepository repository;

    private final Path fileStorageLocation;

    public ConfiguracaoLojaService() {
        // Cria a pasta "uploads" na raiz do projeto se não existir
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Não foi possível criar o diretório de uploads.", ex);
        }
    }

    @Transactional(readOnly = true)
    public ConfiguracaoLoja buscarConfiguracao() {
        return repository.findFirstByOrderByIdAsc()
                .orElseGet(this::criarConfiguracaoPadrao);
    }

    @Transactional
    public ConfiguracaoLoja salvarConfiguracao(ConfiguracaoLoja novaConfig) {
        // Busca a existente ou cria uma padrão com toda a estrutura inicializada
        ConfiguracaoLoja configExistente = repository.findFirstByOrderByIdAsc()
                .orElseGet(this::criarConfiguracaoPadrao);

        // 1. Atualização dos Dados da Loja (Com preservação de Logo)
        if (novaConfig.getLoja() != null) {
            // Se a nova config não trouxe URL de logo, mas a antiga tinha, mantém a antiga
            if ((novaConfig.getLoja().getLogoUrl() == null || novaConfig.getLoja().getLogoUrl().isEmpty())
                    && configExistente.getLoja() != null
                    && configExistente.getLoja().getLogoUrl() != null) {

                novaConfig.getLoja().setLogoUrl(configExistente.getLoja().getLogoUrl());
            }
            configExistente.setLoja(novaConfig.getLoja());
        }

        // 2. Atualização Segura dos outros módulos (Evita NullPointerException se o front mandar parcial)
        if (novaConfig.getEndereco() != null) configExistente.setEndereco(novaConfig.getEndereco());

        if (novaConfig.getFiscal() != null) {
            // Preserva caminho do certificado se não vier no update
            if (novaConfig.getFiscal().getCaminhoCertificado() == null && configExistente.getFiscal() != null) {
                novaConfig.getFiscal().setCaminhoCertificado(configExistente.getFiscal().getCaminhoCertificado());
                novaConfig.getFiscal().setSenhaCert(configExistente.getFiscal().getSenhaCert());
            }
            configExistente.setFiscal(novaConfig.getFiscal());
        }

        if (novaConfig.getFinanceiro() != null) configExistente.setFinanceiro(novaConfig.getFinanceiro());
        if (novaConfig.getVendas() != null) configExistente.setVendas(novaConfig.getVendas());
        if (novaConfig.getSistema() != null) configExistente.setSistema(novaConfig.getSistema());

        return repository.save(configExistente);
    }

    @Transactional
    public void salvarCertificado(MultipartFile file, String senha) {
        ConfiguracaoLoja config = buscarConfiguracao();

        // Garante a existência do objeto antes de setar
        if (config.getFiscal() == null) config.setFiscal(new ConfiguracaoLoja.DadosFiscal());

        String fileName = "cert_" + UUID.randomUUID() + ".pfx";
        salvarArquivoEmDisco(file, fileName);

        config.getFiscal().setCaminhoCertificado(fileName);
        config.getFiscal().setSenhaCert(senha);

        repository.save(config);
    }

    @Transactional
    public String salvarLogo(MultipartFile file) {
        ConfiguracaoLoja config = buscarConfiguracao();

        // Garante a existência do objeto antes de setar
        if (config.getLoja() == null) config.setLoja(new ConfiguracaoLoja.DadosLoja());

        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = "logo_" + UUID.randomUUID() + extension;

        salvarArquivoEmDisco(file, fileName);

        String fileUrl = "/uploads/" + fileName;
        config.getLoja().setLogoUrl(fileUrl);

        repository.save(config);

        return fileUrl;
    }

    private void salvarArquivoEmDisco(MultipartFile file, String fileName) {
        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Erro ao salvar arquivo " + fileName, ex);
        }
    }

    private String getFileExtension(String filename) {
        return filename != null && filename.contains(".") ? filename.substring(filename.lastIndexOf(".")) : ".png";
    }

    // Método CRÍTICO: Garante que nunca existam nulos no banco
    private ConfiguracaoLoja criarConfiguracaoPadrao() {
        ConfiguracaoLoja config = new ConfiguracaoLoja();

        config.setLoja(new ConfiguracaoLoja.DadosLoja());
        config.setEndereco(new ConfiguracaoLoja.EnderecoLoja());
        config.setFiscal(new ConfiguracaoLoja.DadosFiscal());
        config.setFinanceiro(new ConfiguracaoLoja.DadosFinanceiro());
        config.setVendas(new ConfiguracaoLoja.DadosVendas());
        config.setSistema(new ConfiguracaoLoja.DadosSistema());

        // Defaults
        config.getSistema().setTema("light");
        config.getSistema().setImpressaoAuto(true);
        config.getFiscal().setAmbiente("HOMOLOGACAO");

        return repository.save(config);
    }
}