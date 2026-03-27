package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
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

    private ConfiguracaoLoja.DadosVendas converterVendas(ConfiguracaoDTO.VendasDTO dto) {
        if (dto == null) return new ConfiguracaoLoja.DadosVendas();
        ConfiguracaoLoja.DadosVendas vendas = new ConfiguracaoLoja.DadosVendas();
        vendas.setComportamentoCpf(dto.comportamentoCpf());
        vendas.setBloquearEstoque(dto.bloquearEstoque());
        vendas.setLayoutCupom(dto.layoutCupom());
        vendas.setImprimirVendedor(dto.imprimirVendedor());
        vendas.setImprimirTicketTroca(dto.imprimirTicketTroca());
        vendas.setAutoEnterScanner(dto.autoEnterScanner());
        vendas.setFidelidadeAtiva(dto.fidelidadeAtiva());
        vendas.setPontosPorReal(dto.pontosPorReal());
        vendas.setUsarBalanca(dto.usarBalanca());
        vendas.setAgruparItens(dto.agruparItens());
        vendas.setMetaMensal(dto.metaMensal() != null ? dto.metaMensal() : BigDecimal.ZERO);
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
    public ConfiguracaoDTO salvar(ConfiguracaoDTO dto) {
        ConfiguracaoLoja config = buscarConfiguracao();
        atualizarEntidade(config, dto);
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

        // 🚨 GATILHO DA LIMPEZA: Apaga o certificado antigo antes de salvar o novo
        apagarArquivoAntigo(config.getFiscal().getCaminhoCertificado());

        String fileName = "cert_" + UUID.randomUUID() + ".pfx";
        salvarArquivoEmDisco(file, fileName);

        // SALVA OS DADOS BINÁRIOS NA BASE DE DADOS E O CAMINHO
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
    public String salvarLogo(MultipartFile file) {
        ConfiguracaoLoja config = buscarConfiguracao();

        // 🚨 GATILHO DA LIMPEZA: Apaga a logo antiga antes de salvar a nova
        apagarArquivoAntigo(config.getLoja().getLogoUrl());

        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = "logo_" + UUID.randomUUID() + extension;
        salvarArquivoEmDisco(file, fileName);
        String fileUrl = "/uploads/" + fileName;
        config.getLoja().setLogoUrl(fileUrl);
        repository.save(config);
        return fileUrl;
    }

    /**
     * Limpa o arquivo físico da pasta uploads se o sistema for substituí-lo
     */
    private void apagarArquivoAntigo(String nomeOuUrlSalvoNoBanco) {
        if (nomeOuUrlSalvoNoBanco == null || nomeOuUrlSalvoNoBanco.isBlank()) {
            return;
        }
        try {
            // Remove o mapeamento web para sobrar apenas o nome do arquivo, ex: "logo_123.png"
            String nomeLimpo = nomeOuUrlSalvoNoBanco.replace("/uploads/", "").replace("uploads/", "");

            Path caminhoDoArquivo = this.fileStorageLocation.resolve(nomeLimpo).normalize();
            boolean apagou = Files.deleteIfExists(caminhoDoArquivo);

            if (apagou) {
                log.info("🗑️ Arquivo antigo removido da pasta uploads: {}", nomeLimpo);
            }
        } catch (Exception e) {
            log.warn("⚠️ Aviso: Não foi possível apagar o arquivo antigo ({}). Motivo: {}", nomeOuUrlSalvoNoBanco, e.getMessage());
        }
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

    private void atualizarEntidade(ConfiguracaoLoja c, ConfiguracaoDTO d) {
        c.garantirInstancias();

        String logoAtual = c.getLoja().getLogoUrl();
        c.setLoja(new ConfiguracaoLoja.DadosLoja(
                d.loja().razaoSocial(), d.loja().nomeFantasia(), d.loja().cnpj(), d.loja().ie(), d.loja().im(), d.loja().cnae(),
                d.loja().email(), d.loja().telefone(), d.loja().whatsapp(), d.loja().site(), d.loja().instagram(), d.loja().slogan(),
                d.loja().corDestaque(), d.loja().isMatriz(), d.loja().horarioAbre(), d.loja().horarioFecha(),
                d.loja().toleranciaMinutos(), d.loja().bloqueioForaHorario(), d.loja().taxaEntregaPadrao(), d.loja().tempoEntregaMin(),
                d.loja().logoUrl() != null && !d.loja().logoUrl().isEmpty() ? d.loja().logoUrl() : logoAtual
        ));

        c.setEndereco(new ConfiguracaoLoja.EnderecoLoja(
                d.endereco().cep(), d.endereco().logradouro(), d.endereco().numero(), d.endereco().complemento(),
                d.endereco().bairro(), d.endereco().cidade(), d.endereco().uf()
        ));

        ConfiguracaoLoja.DadosFiscal f = c.getFiscal();

        String senhaCertAtual = f.getSenhaCert();
        String caminhoCertAtual = f.getCaminhoCertificado();
        byte[] arquivoCertAtual = f.getArquivoCertificado();

        f.setAmbiente(d.fiscal().ambiente());
        f.setRegime(d.fiscal().regime());

        // A MÁGICA: Mapeamento plano sem intermediários
        f.setTokenHomologacao(d.fiscal().tokenHomologacao());
        f.setCscIdHomologacao(d.fiscal().cscIdHomologacao());
        f.setSerieHomologacao(d.fiscal().serieHomologacao() != null ? d.fiscal().serieHomologacao() : 1);
        f.setNfeHomologacao(d.fiscal().nfeHomologacao() != null ? d.fiscal().nfeHomologacao() : 1);

        f.setTokenProducao(d.fiscal().tokenProducao());
        f.setCscIdProducao(d.fiscal().cscIdProducao());
        f.setSerieProducao(d.fiscal().serieProducao() != null ? d.fiscal().serieProducao() : 1);
        f.setNfeProducao(d.fiscal().nfeProducao() != null ? d.fiscal().nfeProducao() : 1);

        if (d.fiscal().senhaCert() != null && !d.fiscal().senhaCert().isEmpty()) {
            f.setSenhaCert(d.fiscal().senhaCert());
        } else {
            f.setSenhaCert(senhaCertAtual);
        }

        if (d.fiscal().caminhoCertificado() != null && !d.fiscal().caminhoCertificado().isEmpty()) {
            f.setCaminhoCertificado(d.fiscal().caminhoCertificado());
        } else {
            f.setCaminhoCertificado(caminhoCertAtual);
        }

        f.setArquivoCertificado(arquivoCertAtual);

        f.setCsrtId(d.fiscal().csrtId());
        f.setCsrtHash(d.fiscal().csrtHash());
        f.setIbptToken(d.fiscal().ibptToken());
        f.setNaturezaPadrao(d.fiscal().naturezaPadrao());
        f.setEmailContabil(d.fiscal().emailContabil());
        f.setEnviarXmlAutomatico(d.fiscal().enviarXmlAutomatico());
        f.setAliquotaInterna(d.fiscal().aliquotaInterna());
        f.setModoContingencia(d.fiscal().modoContingencia());
        f.setPriorizarMonofasico(d.fiscal().priorizarMonofasico());
        f.setObsPadraoCupom(d.fiscal().obsPadraoCupom());

        ConfiguracaoLoja.DadosFinanceiro fin = c.getFinanceiro();
        fin.setComissaoProdutos(d.financeiro().comissaoProdutos());
        fin.setComissaoServicos(d.financeiro().comissaoServicos());
        fin.setAlertaSangria(d.financeiro().alertaSangria());
        fin.setFundoTrocoPadrao(d.financeiro().fundoTrocoPadrao());
        fin.setMetaDiaria(d.financeiro().metaDiaria());
        fin.setTaxaDebito(d.financeiro().taxaDebito());
        fin.setTaxaCredito(d.financeiro().taxaCredito());
        fin.setDescCaixa(d.financeiro().descCaixa());
        fin.setDescGerente(d.financeiro().descGerente());
        fin.setDescExtraPix(d.financeiro().descExtraPix());
        fin.setBloquearAbaixoCusto(d.financeiro().bloquearAbaixoCusto());
        fin.setPixTipo(d.financeiro().pixTipo());
        fin.setPixChave(d.financeiro().pixChave());
        fin.setJurosMensal(d.financeiro().jurosMensal());
        fin.setMultaAtraso(d.financeiro().multaAtraso());
        fin.setDiasCarencia(d.financeiro().diasCarencia());
        fin.setFechamentoCego(d.financeiro().fechamentoCego());

        if (d.financeiro().pagamentos() != null) {
            fin.setAceitaDinheiro(d.financeiro().pagamentos().dinheiro());
            fin.setAceitaPix(d.financeiro().pagamentos().pix());
            fin.setAceitaCredito(d.financeiro().pagamentos().credito());
            fin.setAceitaDebito(d.financeiro().pagamentos().debito());
            fin.setAceitaCrediario(d.financeiro().pagamentos().crediario());
        }

        c.setVendas(converterVendas(d.vendas()));

        c.setSistema(new ConfiguracaoLoja.DadosSistema(
                d.sistema().impressaoAuto(), d.sistema().larguraPapel(), d.sistema().backupAuto(), d.sistema().backupHora(),
                d.sistema().rodape(), d.sistema().tema(), d.sistema().backupNuvem(), d.sistema().senhaGerenteCancelamento(),
                d.sistema().nomeTerminal(), d.sistema().imprimirLogoCupom()
        ));
    }

    private ConfiguracaoDTO converterParaDTO(ConfiguracaoLoja c) {
        return new ConfiguracaoDTO(
                c.getId(),
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

                        c.getFiscal().getCaminhoCertificado(), "", // SEGURANÇA: NÃO DEVOLVE A SENHA
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
                        new ConfiguracaoDTO.PagamentosDTO(
                                c.getFinanceiro().getAceitaDinheiro(), c.getFinanceiro().getAceitaPix(),
                                c.getFinanceiro().getAceitaCredito(), c.getFinanceiro().getAceitaDebito(), c.getFinanceiro().getAceitaCrediario()
                        ),
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
                )
        );
    }

    private ConfiguracaoLoja criarConfiguracaoPadrao() {
        ConfiguracaoLoja config = new ConfiguracaoLoja();
        config.garantirInstancias();
        config.getSistema().setTema("light");
        config.getSistema().setImpressaoAuto(true);
        config.getFiscal().setAmbiente("HOMOLOGACAO");
        return repository.save(config);
    }
}