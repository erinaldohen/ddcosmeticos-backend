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
        // Define onde salvar as imagens/certificados (pasta 'uploads' na raiz do projeto)
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Não foi possível criar o diretório de uploads.", ex);
        }
    }

    public ConfiguracaoLoja buscarConfiguracao() {
        return repository.findFirstByOrderByIdAsc()
                .orElseGet(this::criarConfiguracaoPadrao);
    }

    @Transactional
    public ConfiguracaoLoja salvarConfiguracao(ConfiguracaoLoja novaConfig) {
        ConfiguracaoLoja configExistente = repository.findFirstByOrderByIdAsc()
                .orElse(new ConfiguracaoLoja());

        // Mantém a logo antiga se não vier uma nova (lógica de segurança)
        if (configExistente.getLoja() != null &&
                (novaConfig.getLoja() != null && (novaConfig.getLoja().getLogoUrl() == null || novaConfig.getLoja().getLogoUrl().isEmpty()))) {
            novaConfig.getLoja().setLogoUrl(configExistente.getLoja().getLogoUrl());
        }

        // Atualiza os objetos embutidos
        configExistente.setLoja(novaConfig.getLoja());
        configExistente.setEndereco(novaConfig.getEndereco());
        configExistente.setFiscal(novaConfig.getFiscal());
        configExistente.setFinanceiro(novaConfig.getFinanceiro());
        configExistente.setVendas(novaConfig.getVendas());
        configExistente.setSistema(novaConfig.getSistema());

        return repository.save(configExistente);
    }

    public void salvarCertificado(MultipartFile file, String senha) {
        ConfiguracaoLoja config = buscarConfiguracao();

        // CORREÇÃO AQUI: Usando ConfiguracaoLoja.DadosFiscal
        if (config.getFiscal() == null) config.setFiscal(new ConfiguracaoLoja.DadosFiscal());

        String fileName = "cert_" + UUID.randomUUID() + ".pfx";
        salvarArquivoEmDisco(file, fileName);

        config.getFiscal().setCaminhoCertificado(fileName);
        config.getFiscal().setSenhaCert(senha);

        repository.save(config);
    }

    public String salvarLogo(MultipartFile file) {
        ConfiguracaoLoja config = buscarConfiguracao();

        // CORREÇÃO AQUI: Usando ConfiguracaoLoja.DadosLoja
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

    private ConfiguracaoLoja criarConfiguracaoPadrao() {
        ConfiguracaoLoja config = new ConfiguracaoLoja();

        // CORREÇÃO AQUI: Instanciando as classes internas corretamente
        config.setLoja(new ConfiguracaoLoja.DadosLoja());
        config.setEndereco(new ConfiguracaoLoja.EnderecoLoja());
        config.setFiscal(new ConfiguracaoLoja.DadosFiscal());
        config.setFinanceiro(new ConfiguracaoLoja.DadosFinanceiro());
        config.setVendas(new ConfiguracaoLoja.DadosVendas());
        config.setSistema(new ConfiguracaoLoja.DadosSistema());

        // Define padrões
        config.getSistema().setImpressaoAuto(true);
        config.getSistema().setLarguraPapel("80mm");
        config.getSistema().setTema("light");

        // Padrões Fiscais básicos para evitar NullPointer
        config.getFiscal().setAmbiente("HOMOLOGACAO");

        return repository.save(config);
    }
}