package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ConfiguracaoDTO;
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

    // --- MÉTODOS DE BUSCA ---

    @Transactional(readOnly = true)
    public ConfiguracaoLoja buscarConfiguracao() {
        return repository.findFirstByOrderByIdAsc()
                .orElseGet(this::criarConfiguracaoPadrao);
    }

    @Transactional(readOnly = true)
    public ConfiguracaoDTO buscarConfiguracaoDTO() {
        ConfiguracaoLoja config = buscarConfiguracao();
        return converterParaDTO(config);
    }

    // --- MÉTODOS DE SALVAMENTO (JSON) ---

    @Transactional
    public ConfiguracaoDTO salvar(ConfiguracaoDTO dto) {
        ConfiguracaoLoja config = buscarConfiguracao(); // Recupera a existente
        atualizarEntidade(config, dto); // Aplica as mudanças do DTO na Entidade
        ConfiguracaoLoja salva = repository.save(config);
        return converterParaDTO(salva);
    }

    // --- MÉTODOS DE ARQUIVOS (UPLOAD) ---

    @Transactional
    public void salvarCertificado(MultipartFile file, String senha) {
        ConfiguracaoLoja config = buscarConfiguracao();
        config.preencherNulos(); // Garante estrutura

        String fileName = "cert_" + UUID.randomUUID() + ".pfx";
        salvarArquivoEmDisco(file, fileName);

        config.getFiscal().setCaminhoCertificado(fileName);
        config.getFiscal().setSenhaCert(senha);

        repository.save(config);
    }

    @Transactional
    public String salvarLogo(MultipartFile file) {
        ConfiguracaoLoja config = buscarConfiguracao();
        config.preencherNulos();

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

    // --- MAPEAMENTO MANUAL (DTO <-> ENTIDADE) ---

    private void atualizarEntidade(ConfiguracaoLoja c, ConfiguracaoDTO d) {
        c.preencherNulos();

        // 1. LOJA (Preservando Logo se não vier no DTO)
        String logoAtual = c.getLoja().getLogoUrl();
        c.setLoja(new ConfiguracaoLoja.DadosLoja(
                d.loja().razaoSocial(), d.loja().nomeFantasia(), d.loja().cnpj(), d.loja().ie(), d.loja().im(), d.loja().cnae(),
                d.loja().email(), d.loja().telefone(), d.loja().whatsapp(), d.loja().site(), d.loja().instagram(), d.loja().slogan(),
                d.loja().corDestaque(), d.loja().isMatriz(), d.loja().horarioAbre(), d.loja().horarioFecha(),
                d.loja().toleranciaMinutos(), d.loja().bloqueioForaHorario(), d.loja().taxaEntregaPadrao(), d.loja().tempoEntregaMin(),
                d.loja().logoUrl() // Tenta pegar do DTO
        ));
        // Se o DTO veio com logo vazia, restaura a antiga
        if (d.loja().logoUrl() == null || d.loja().logoUrl().isEmpty()) {
            c.getLoja().setLogoUrl(logoAtual);
        }

        // 2. ENDERECO
        c.setEndereco(new ConfiguracaoLoja.EnderecoLoja(
                d.endereco().cep(), d.endereco().logradouro(), d.endereco().numero(), d.endereco().complemento(),
                d.endereco().bairro(), d.endereco().cidade(), d.endereco().uf()
        ));

        // 3. FISCAL (Preservando Certificado)
        String certAtual = c.getFiscal().getCaminhoCertificado();
        String senhaAtual = c.getFiscal().getSenhaCert();

        ConfiguracaoLoja.DadosFiscal f = new ConfiguracaoLoja.DadosFiscal();
        f.setAmbiente(d.fiscal().ambiente());
        f.setRegime(d.fiscal().regime());

        if (d.fiscal().homologacao() != null) {
            f.setTokenHomologacao(d.fiscal().homologacao().token());
            f.setCscIdHomologacao(d.fiscal().homologacao().cscId());
            f.setSerieHomologacao(d.fiscal().homologacao().serie());
            f.setNfeHomologacao(d.fiscal().homologacao().nfe());
        }
        if (d.fiscal().producao() != null) {
            f.setTokenProducao(d.fiscal().producao().token());
            f.setCscIdProducao(d.fiscal().producao().cscId());
            f.setSerieProducao(d.fiscal().producao().serie());
            f.setNfeProducao(d.fiscal().producao().nfe());
        }

        // Restaura certificado se não vier no DTO
        f.setCaminhoCertificado(d.fiscal().caminhoCertificado() != null ? d.fiscal().caminhoCertificado() : certAtual);
        f.setSenhaCert(d.fiscal().senhaCert() != null ? d.fiscal().senhaCert() : senhaAtual);

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
        c.setFiscal(f);

        // 4. FINANCEIRO
        ConfiguracaoLoja.DadosFinanceiro fin = new ConfiguracaoLoja.DadosFinanceiro();
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
            fin.setAceitaDinheiro(Boolean.TRUE.equals(d.financeiro().pagamentos().dinheiro()));
            fin.setAceitaPix(d.financeiro().pagamentos().pix());
            fin.setAceitaCredito(d.financeiro().pagamentos().credito());
            fin.setAceitaDebito(d.financeiro().pagamentos().debito());
            fin.setAceitaCrediario(d.financeiro().pagamentos().crediario());
        }
        c.setFinanceiro(fin);

        // 5. VENDAS
        c.setVendas(new ConfiguracaoLoja.DadosVendas(
                d.vendas().comportamentoCpf(), d.vendas().bloquearEstoque(), d.vendas().layoutCupom(),
                d.vendas().imprimirVendedor(), d.vendas().imprimirTicketTroca(), d.vendas().autoEnterScanner(),
                d.vendas().fidelidadeAtiva(), d.vendas().pontosPorReal(), d.vendas().usarBalanca(), d.vendas().agruparItens()
        ));

        // 6. SISTEMA
        c.setSistema(new ConfiguracaoLoja.DadosSistema(
                d.sistema().impressaoAuto(), d.sistema().larguraPapel(), d.sistema().backupAuto(), d.sistema().backupHora(),
                d.sistema().rodape(), d.sistema().tema(), d.sistema().backupNuvem(), d.sistema().senhaGerenteCancelamento(),
                d.sistema().nomeTerminal(), d.sistema().imprimirLogoCupom()
        ));
    }

    private ConfiguracaoDTO converterParaDTO(ConfiguracaoLoja c) {
        c.preencherNulos();

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
                        new ConfiguracaoDTO.FiscalAmbienteDTO(c.getFiscal().getTokenHomologacao(), c.getFiscal().getCscIdHomologacao(), c.getFiscal().getSerieHomologacao(), c.getFiscal().getNfeHomologacao()),
                        new ConfiguracaoDTO.FiscalAmbienteDTO(c.getFiscal().getTokenProducao(), c.getFiscal().getCscIdProducao(), c.getFiscal().getSerieProducao(), c.getFiscal().getNfeProducao()),
                        c.getFiscal().getCaminhoCertificado(), c.getFiscal().getSenhaCert(),
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
                        c.getVendas().getFidelidadeAtiva(), c.getVendas().getPontosPorReal(), c.getVendas().getUsarBalanca(), c.getVendas().getAgruparItens()
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
        config.preencherNulos();
        config.getSistema().setTema("light");
        config.getSistema().setImpressaoAuto(true);
        config.getFiscal().setAmbiente("HOMOLOGACAO");
        return repository.save(config);
    }
}