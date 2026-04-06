package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ValidationException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.ConfiguracaoLoja;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ConfiguracaoLojaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ConfiguracaoLojaService {

    @Autowired
    private ConfiguracaoLojaRepository repository;

    private final Path fileStorageLocation;

    public ConfiguracaoLojaService() {
        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Não foi possível criar o diretório de uploads.", ex);
        }
    }

    private ConfiguracaoLoja.DadosVendas converterVendas(ConfiguracaoDTO.VendasDTO vendasDTO) {
        if (vendasDTO == null) return new ConfiguracaoLoja.DadosVendas();
        ConfiguracaoLoja.DadosVendas vendas = new ConfiguracaoLoja.DadosVendas();
        vendas.setComportamentoCpf(vendasDTO.comportamentoCpf());
        vendas.setBloquearEstoque(vendasDTO.bloquearEstoque());
        vendas.setLayoutCupom(vendasDTO.layoutCupom());
        vendas.setImprimirVendedor(vendasDTO.imprimirVendedor());
        vendas.setImprimirTicketTroca(vendasDTO.imprimirTicketTroca());
        vendas.setAutoEnterScanner(vendasDTO.autoEnterScanner());
        vendas.setFidelidadeAtiva(vendasDTO.fidelidadeAtiva());
        vendas.setPontosPorReal(vendasDTO.pontosPorReal());
        vendas.setUsarBalanca(vendasDTO.usarBalanca());
        vendas.setAgruparItens(vendasDTO.agruparItens());
        vendas.setMetaMensal(vendasDTO.metaMensal() != null ? vendasDTO.metaMensal() : BigDecimal.ZERO);
        return vendas;
    }

    @Transactional
    public ConfiguracaoLoja buscarConfiguracao() {
        return repository.findAll().stream()
                .findFirst()
                .map(config -> {
                    config.garantirInstancias();
                    return config;
                })
                .orElseGet(this::criarConfiguracaoPadrao);
    }

    @Transactional
    public ConfiguracaoDTO buscarConfiguracaoDTO() {
        return converterParaDTO(buscarConfiguracao());
    }

    @Transactional
    public ConfiguracaoDTO salvar(ConfiguracaoDTO configuracaoDTO) {
        ConfiguracaoLoja config = buscarConfiguracao();
        atualizarEntidade(config, configuracaoDTO);
        ConfiguracaoLoja salva = repository.save(config);
        return converterParaDTO(salva);
    }

    @Transactional
    public Map<String, Object> salvarCertificado(MultipartFile file, String senha) throws Exception {
        log.info("Processando upload de novo Certificado Digital A1...");
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(file.getInputStream(), senha.toCharArray());

        Enumeration<String> aliases = ks.aliases();
        if (!aliases.hasMoreElements()) {
            throw new RuntimeException("Nenhum certificado encontrado dentro do arquivo PFX.");
        }

        String alias = aliases.nextElement();
        X509Certificate certificate = (X509Certificate) ks.getCertificate(alias);

        Date dataExpiracao = certificate.getNotAfter();
        LocalDate dataValidadeLocal = dataExpiracao.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        long diasRestantes = ChronoUnit.DAYS.between(LocalDate.now(), dataValidadeLocal);

        ConfiguracaoLoja config = buscarConfiguracao();
        apagarArquivoAntigo(config.getFiscal().getCaminhoCertificado());

        String fileName = "cert_" + UUID.randomUUID() + ".pfx";
        salvarArquivoEmDisco(file, fileName);

        config.getFiscal().setCaminhoCertificado(fileName);
        config.getFiscal().setSenhaCert(senha);
        config.getFiscal().setArquivoCertificado(file.getBytes());

        repository.save(config);
        log.info("Certificado salvo com sucesso. Validade: {} dias.", diasRestantes);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Map<String, Object> response = new HashMap<>();
        response.put("validade", dataValidadeLocal.format(formatter));
        response.put("diasRestantes", diasRestantes);

        return response;
    }

    @Transactional
    public String salvarLogo(MultipartFile multipartFile) {
        String contentType = multipartFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ValidationException("Apenas arquivos de imagem são permitidos.");
        }

        ConfiguracaoLoja config = buscarConfiguracao();
        apagarArquivoAntigo(config.getLoja().getLogoUrl());

        String extension = getFileExtension(multipartFile.getOriginalFilename());
        String fileName = "logo_" + UUID.randomUUID() + extension;
        salvarArquivoEmDisco(multipartFile, fileName);
        String fileUrl = "/uploads/" + fileName;
        config.getLoja().setLogoUrl(fileUrl);
        repository.save(config);
        return fileUrl;
    }

    private void apagarArquivoAntigo(String nomeOuUrlSalvoNoBanco) {
        if (nomeOuUrlSalvoNoBanco == null || nomeOuUrlSalvoNoBanco.isBlank()) return;
        try {
            String nomeLimpo = nomeOuUrlSalvoNoBanco.replace("/uploads/", "").replace("uploads/", "");
            Path caminhoDoArquivo = this.fileStorageLocation.resolve(nomeLimpo).normalize();
            Files.deleteIfExists(caminhoDoArquivo);
        } catch (Exception e) {
            log.warn("Não foi possível apagar o arquivo antigo.");
        }
    }

    private void salvarArquivoEmDisco(MultipartFile file, String fileName) {
        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Erro ao salvar arquivo", ex);
        }
    }

    private String getFileExtension(String filename) {
        return filename != null && filename.contains(".") ? filename.substring(filename.lastIndexOf(".")) : ".png";
    }

    private void atualizarEntidade(ConfiguracaoLoja configuracaoLoja, ConfiguracaoDTO configuracaoDTO) {
        configuracaoLoja.garantirInstancias();

        // 🚨 1. SALVANDO E-MAIL (SMTP) E INTEGRAÇÕES NA RAIZ DA ENTIDADE
        if (configuracaoDTO.smtpHost() != null) configuracaoLoja.setSmtpHost(configuracaoDTO.smtpHost());
        if (configuracaoDTO.smtpPort() != null) configuracaoLoja.setSmtpPort(configuracaoDTO.smtpPort());
        if (configuracaoDTO.smtpUsername() != null) configuracaoLoja.setSmtpUsername(configuracaoDTO.smtpUsername());

        // Só atualiza a senha de e-mail se o usuário digitou uma nova (proteção de frontend)
        if (configuracaoDTO.smtpPassword() != null && !configuracaoDTO.smtpPassword().isBlank()) {
            configuracaoLoja.setSmtpPassword(configuracaoDTO.smtpPassword());
        }

        if (configuracaoDTO.gatewayPagamento() != null) configuracaoLoja.setGatewayPagamento(configuracaoDTO.gatewayPagamento());
        if (configuracaoDTO.infinitepayClientId() != null) configuracaoLoja.setInfinitepayClientId(configuracaoDTO.infinitepayClientId());
        if (configuracaoDTO.infinitepayClientSecret() != null) configuracaoLoja.setInfinitepayClientSecret(configuracaoDTO.infinitepayClientSecret());
        if (configuracaoDTO.infinitepayWalletId() != null) configuracaoLoja.setInfinitepayWalletId(configuracaoDTO.infinitepayWalletId());
        // -------------------------------------------------------------

        String logoAtual = configuracaoLoja.getLoja().getLogoUrl();
        configuracaoLoja.setLoja(new ConfiguracaoLoja.DadosLoja(
                configuracaoDTO.loja().razaoSocial(), configuracaoDTO.loja().nomeFantasia(), configuracaoDTO.loja().cnpj(), configuracaoDTO.loja().ie(), configuracaoDTO.loja().im(), configuracaoDTO.loja().cnae(),
                configuracaoDTO.loja().email(), configuracaoDTO.loja().telefone(), configuracaoDTO.loja().whatsapp(), configuracaoDTO.loja().site(), configuracaoDTO.loja().instagram(), configuracaoDTO.loja().slogan(),
                configuracaoDTO.loja().corDestaque(), configuracaoDTO.loja().isMatriz(), configuracaoDTO.loja().horarioAbre(), configuracaoDTO.loja().horarioFecha(),
                configuracaoDTO.loja().toleranciaMinutos(), configuracaoDTO.loja().bloqueioForaHorario(), configuracaoDTO.loja().taxaEntregaPadrao(), configuracaoDTO.loja().tempoEntregaMin(),
                configuracaoDTO.loja().logoUrl() != null && !configuracaoDTO.loja().logoUrl().isEmpty() ? configuracaoDTO.loja().logoUrl() : logoAtual
        ));

        configuracaoLoja.setEndereco(new ConfiguracaoLoja.EnderecoLoja(
                configuracaoDTO.endereco().cep(), configuracaoDTO.endereco().logradouro(), configuracaoDTO.endereco().numero(), configuracaoDTO.endereco().complemento(),
                configuracaoDTO.endereco().bairro(), configuracaoDTO.endereco().cidade(), configuracaoDTO.endereco().uf()
        ));

        ConfiguracaoLoja.DadosFiscal f = configuracaoLoja.getFiscal();

        String senhaCertAtual = f.getSenhaCert();
        String caminhoCertAtual = f.getCaminhoCertificado();
        byte[] arquivoCertAtual = f.getArquivoCertificado();

        f.setAmbiente(configuracaoDTO.fiscal().ambiente());
        f.setRegime(configuracaoDTO.fiscal().regime());

        f.setTokenHomologacao(configuracaoDTO.fiscal().tokenHomologacao());
        f.setCscIdHomologacao(configuracaoDTO.fiscal().cscIdHomologacao());
        f.setSerieHomologacao(configuracaoDTO.fiscal().serieHomologacao() != null ? configuracaoDTO.fiscal().serieHomologacao() : 1);
        f.setNfeHomologacao(configuracaoDTO.fiscal().nfeHomologacao() != null ? configuracaoDTO.fiscal().nfeHomologacao() : 1);

        f.setTokenProducao(configuracaoDTO.fiscal().tokenProducao());
        f.setCscIdProducao(configuracaoDTO.fiscal().cscIdProducao());
        f.setSerieProducao(configuracaoDTO.fiscal().serieProducao() != null ? configuracaoDTO.fiscal().serieProducao() : 1);
        f.setNfeProducao(configuracaoDTO.fiscal().nfeProducao() != null ? configuracaoDTO.fiscal().nfeProducao() : 1);

        f.setSenhaCert(configuracaoDTO.fiscal().senhaCert() != null && !configuracaoDTO.fiscal().senhaCert().isEmpty() ? configuracaoDTO.fiscal().senhaCert() : senhaCertAtual);
        f.setCaminhoCertificado(configuracaoDTO.fiscal().caminhoCertificado() != null && !configuracaoDTO.fiscal().caminhoCertificado().isEmpty() ? configuracaoDTO.fiscal().caminhoCertificado() : caminhoCertAtual);
        f.setArquivoCertificado(arquivoCertAtual);

        f.setCsrtId(configuracaoDTO.fiscal().csrtId());
        f.setCsrtHash(configuracaoDTO.fiscal().csrtHash());
        f.setIbptToken(configuracaoDTO.fiscal().ibptToken());
        f.setNaturezaPadrao(configuracaoDTO.fiscal().naturezaPadrao());
        f.setEmailContabil(configuracaoDTO.fiscal().emailContabil());
        f.setEnviarXmlAutomatico(configuracaoDTO.fiscal().enviarXmlAutomatico());
        f.setAliquotaInterna(configuracaoDTO.fiscal().aliquotaInterna());
        f.setModoContingencia(configuracaoDTO.fiscal().modoContingencia());
        f.setPriorizarMonofasico(configuracaoDTO.fiscal().priorizarMonofasico());
        f.setObsPadraoCupom(configuracaoDTO.fiscal().obsPadraoCupom());

        ConfiguracaoLoja.DadosFinanceiro fin = configuracaoLoja.getFinanceiro();
        fin.setComissaoProdutos(configuracaoDTO.financeiro().comissaoProdutos());
        fin.setComissaoServicos(configuracaoDTO.financeiro().comissaoServicos());
        fin.setAlertaSangria(configuracaoDTO.financeiro().alertaSangria());
        fin.setFundoTrocoPadrao(configuracaoDTO.financeiro().fundoTrocoPadrao());
        fin.setMetaDiaria(configuracaoDTO.financeiro().metaDiaria());
        fin.setTaxaDebito(configuracaoDTO.financeiro().taxaDebito());
        fin.setTaxaCredito(configuracaoDTO.financeiro().taxaCredito());
        fin.setDescCaixa(configuracaoDTO.financeiro().descCaixa());
        fin.setDescGerente(configuracaoDTO.financeiro().descGerente());
        fin.setDescExtraPix(configuracaoDTO.financeiro().descExtraPix());
        fin.setBloquearAbaixoCusto(configuracaoDTO.financeiro().bloquearAbaixoCusto());
        fin.setPixTipo(configuracaoDTO.financeiro().pixTipo());
        fin.setPixChave(configuracaoDTO.financeiro().pixChave());
        fin.setJurosMensal(configuracaoDTO.financeiro().jurosMensal());
        fin.setMultaAtraso(configuracaoDTO.financeiro().multaAtraso());
        fin.setDiasCarencia(configuracaoDTO.financeiro().diasCarencia());
        fin.setFechamentoCego(configuracaoDTO.financeiro().fechamentoCego());

        fin.setAceitaDinheiro(configuracaoDTO.financeiro().aceitaDinheiro() != null ? configuracaoDTO.financeiro().aceitaDinheiro() : true);
        fin.setAceitaPix(configuracaoDTO.financeiro().aceitaPix() != null ? configuracaoDTO.financeiro().aceitaPix() : true);
        fin.setAceitaCredito(configuracaoDTO.financeiro().aceitaCredito() != null ? configuracaoDTO.financeiro().aceitaCredito() : true);
        fin.setAceitaDebito(configuracaoDTO.financeiro().aceitaDebito() != null ? configuracaoDTO.financeiro().aceitaDebito() : true);
        fin.setAceitaCrediario(configuracaoDTO.financeiro().aceitaCrediario() != null ? configuracaoDTO.financeiro().aceitaCrediario() : false);

        configuracaoLoja.setVendas(converterVendas(configuracaoDTO.vendas()));

        configuracaoLoja.setSistema(new ConfiguracaoLoja.DadosSistema(
                configuracaoDTO.sistema().impressaoAuto(), configuracaoDTO.sistema().larguraPapel(), configuracaoDTO.sistema().backupAuto(), configuracaoDTO.sistema().backupHora(),
                configuracaoDTO.sistema().rodape(), configuracaoDTO.sistema().tema(), configuracaoDTO.sistema().backupNuvem(), configuracaoDTO.sistema().senhaGerenteCancelamento(),
                configuracaoDTO.sistema().nomeTerminal(), configuracaoDTO.sistema().imprimirLogoCupom()
        ));

        if (configuracaoDTO.comissoes() != null) {
            configuracaoLoja.setComissoes(new ConfiguracaoLoja.DadosComissoes(
                    configuracaoDTO.comissoes().tipoCalculo(),
                    configuracaoDTO.comissoes().percentualGeral() != null ? configuracaoDTO.comissoes().percentualGeral() : BigDecimal.ZERO,
                    configuracaoDTO.comissoes().comissionarSobre(),
                    configuracaoDTO.comissoes().descontarTaxasCartao() != null ? configuracaoDTO.comissoes().descontarTaxasCartao() : false
            ));
        }
    }

    private ConfiguracaoDTO converterParaDTO(ConfiguracaoLoja c) {
        return new ConfiguracaoDTO(
                c.getId(),

                // 🚨 2. ENVIANDO OS CAMPOS RAIZ PARA O FRONTEND
                c.getSmtpHost() != null ? c.getSmtpHost() : "",
                c.getSmtpPort() != null ? c.getSmtpPort() : 587,
                c.getSmtpUsername() != null ? c.getSmtpUsername() : "",
                "", // Envia a senha em branco por segurança
                c.getGatewayPagamento() != null ? c.getGatewayPagamento() : "MANUAL",
                c.getInfinitepayClientId() != null ? c.getInfinitepayClientId() : "",
                c.getInfinitepayClientSecret() != null ? c.getInfinitepayClientSecret() : "",
                c.getInfinitepayWalletId() != null ? c.getInfinitepayWalletId() : "",
                // -------------------------------------------------------------

                new ConfiguracaoDTO.LojaDTO(
                        c.getLoja().getRazaoSocial(), c.getLoja().getNomeFantasia(), c.getLoja().getCnpj(), c.getLoja().getIe(),
                        c.getLoja().getIm(), c.getLoja().getCnae(), c.getLoja().getEmail(), c.getLoja().getTelefone(),
                        c.getLoja().getWhatsapp(), c.getLoja().getSite(), c.getLoja().getInstagram(), c.getLoja().getSlogan(),
                        c.getLoja().getCorDestaque(), c.getLoja().getIsMatriz(), c.getLoja().getHorarioAbre(), c.getLoja().getHorarioFecha(),
                        c.getLoja().getToleranciaMinutos(), c.getLoja().getBloqueioForaHorario(), c.getLoja().getTaxaEntregaPadrao(),
                        c.getLoja().getTempoEntregaMin(), c.getLoja().getLogoUrl()
                ),
                new ConfiguracaoDTO.EnderecoDTO(
                        c.getEndereco().getCep(), c.getEndereco().getLogradouro(), c.getEndereco().getNumero(),
                        c.getEndereco().getComplemento(), c.getEndereco().getBairro(), c.getEndereco().getCidade(), c.getEndereco().getUf()
                ),
                new ConfiguracaoDTO.FiscalDTO(
                        c.getFiscal().getAmbiente(), c.getFiscal().getRegime(),
                        c.getFiscal().getTokenHomologacao(), c.getFiscal().getCscIdHomologacao(),
                        c.getFiscal().getSerieHomologacao(), c.getFiscal().getNfeHomologacao(),
                        c.getFiscal().getTokenProducao(), c.getFiscal().getCscIdProducao(),
                        c.getFiscal().getSerieProducao(), c.getFiscal().getNfeProducao(),
                        c.getFiscal().getCaminhoCertificado(), "", // Senha do certificado enviada em branco
                        c.getFiscal().getCsrtId(), c.getFiscal().getCsrtHash(),
                        c.getFiscal().getIbptToken(), c.getFiscal().getNaturezaPadrao(), c.getFiscal().getEmailContabil(),
                        c.getFiscal().getEnviarXmlAutomatico(), c.getFiscal().getAliquotaInterna(),
                        c.getFiscal().getModoContingencia(), c.getFiscal().getPriorizarMonofasico(), c.getFiscal().getObsPadraoCupom()
                ),
                new ConfiguracaoDTO.FinanceiroDTO(
                        c.getFinanceiro().getComissaoProdutos(), c.getFinanceiro().getComissaoServicos(), c.getFinanceiro().getAlertaSangria(),
                        c.getFinanceiro().getFundoTrocoPadrao(), c.getFinanceiro().getMetaDiaria(),
                        c.getFinanceiro().getTaxaDebito(), c.getFinanceiro().getTaxaCredito(),
                        c.getFinanceiro().getDescCaixa(), c.getFinanceiro().getDescGerente(),
                        c.getFinanceiro().getDescExtraPix(), c.getFinanceiro().getBloquearAbaixoCusto(),
                        c.getFinanceiro().getPixTipo(), c.getFinanceiro().getPixChave(),
                        c.getFinanceiro().getAceitaDinheiro(), c.getFinanceiro().getAceitaPix(),
                        c.getFinanceiro().getAceitaCredito(), c.getFinanceiro().getAceitaDebito(), c.getFinanceiro().getAceitaCrediario(),
                        c.getFinanceiro().getJurosMensal(), c.getFinanceiro().getMultaAtraso(),
                        c.getFinanceiro().getDiasCarencia(), c.getFinanceiro().getFechamentoCego()
                ),
                new ConfiguracaoDTO.VendasDTO(
                        c.getVendas().getComportamentoCpf(), c.getVendas().getBloquearEstoque(), c.getVendas().getLayoutCupom(),
                        c.getVendas().getImprimirVendedor(), c.getVendas().getImprimirTicketTroca(), c.getVendas().getAutoEnterScanner(),
                        c.getVendas().getFidelidadeAtiva(), c.getVendas().getPontosPorReal(),
                        c.getVendas().getUsarBalanca(), c.getVendas().getAgruparItens(),
                        c.getVendas().getMetaMensal()
                ),
                new ConfiguracaoDTO.SistemaDTO(
                        c.getSistema().getImpressaoAuto(), c.getSistema().getLarguraPapel(), c.getSistema().getBackupAuto(),
                        c.getSistema().getBackupHora(), c.getSistema().getRodape(), c.getSistema().getTema(),
                        c.getSistema().getBackupNuvem(), c.getSistema().getSenhaGerenteCancelamento(),
                        c.getSistema().getNomeTerminal(), c.getSistema().getImprimirLogoCupom()
                ),
                new ConfiguracaoDTO.ComissoesDTO(
                        c.getComissoes() != null && c.getComissoes().getTipoCalculo() != null ? c.getComissoes().getTipoCalculo() : "GERAL",
                        c.getComissoes() != null && c.getComissoes().getPercentualGeral() != null ? c.getComissoes().getPercentualGeral() : BigDecimal.ZERO,
                        c.getComissoes() != null && c.getComissoes().getComissionarSobre() != null ? c.getComissoes().getComissionarSobre() : "LUCRO",
                        c.getComissoes() != null && c.getComissoes().getDescontarTaxasCartao() != null ? c.getComissoes().getDescontarTaxasCartao() : false
                )
        );
    }

    private ConfiguracaoLoja criarConfiguracaoPadrao() {
        ConfiguracaoLoja config = new ConfiguracaoLoja();
        config.garantirInstancias();
        config.getSistema().setTema("light");
        config.getSistema().setImpressaoAuto(true);
        config.getFiscal().setAmbiente("HOMOLOGACAO");
        config.setGatewayPagamento("MANUAL"); // Define o manual como padrão inicial

        return repository.save(config);
    }
}