package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendaService {

    private final ImpressaoService impressaoService;
    private final VendaRepository vendaRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final TituloReceberRepository tituloReceberRepository;
    private final ConfiguracaoLojaService configuracaoLojaService;
    private final CaixaDiarioRepository caixaRepository;
    private final EstoqueService estoqueService;
    private final FinanceiroService financeiroService;
    private final NfceService nfceService;
    private final NfeService nfeService;
    private final AuditoriaService auditoriaService;

    @Transactional(rollbackFor = Exception.class)
    public VendaResponseDTO realizarVenda(VendaRequestDTO dto) {
        Usuario usuarioLogado = capturarUsuarioLogado();
        if (usuarioLogado == null) throw new ValidationException("Sessão inválida ou expirada.");

        CaixaDiario caixa = validarCaixaAberto(usuarioLogado);

        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
        if (config == null || config.getLoja() == null || config.getLoja().getCnpj() == null || config.getLoja().getCnpj().isBlank()) {
            throw new ValidationException("Operação bloqueada: Configure o CNPJ da empresa no menu Configurações antes de realizar vendas.");
        }

        if (dto.pagamentos() == null || dto.pagamentos().isEmpty()) {
            throw new ValidationException("É necessário informar ao menos uma forma de pagamento.");
        }

        if (config.getFiscal() == null || config.getFiscal().getCaminhoCertificado() == null || config.getFiscal().getCaminhoCertificado().isBlank()) {
            throw new ValidationException("Operação bloqueada: Instale o Certificado Digital A1 no menu Configurações.");
        }

        Venda venda = new Venda();
        venda.setUsuario(usuarioLogado);
        venda.setDataVenda(LocalDateTime.now());
        venda.setCaixa(caixa);

        String clientePadraoCupom = config != null && config.getFiscal() != null && config.getFiscal().getObsPadraoCupom() != null
                ? config.getFiscal().getObsPadraoCupom()
                : "Consumidor Não Identificado";

        // LÓGICA DE IDENTIFICAÇÃO DO CLIENTE
        if (dto.clienteId() != null) {
            Cliente cliente = clienteRepository.findById(dto.clienteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente não encontrado ID: " + dto.clienteId()));
            venda.setCliente(cliente);
            venda.setClienteNome(cliente.getNome());
            venda.setClienteDocumento(cliente.getDocumento());
            venda.setClienteTelefone(cliente.getTelefone());

        } else if (dto.clienteDocumento() != null && !dto.clienteDocumento().isBlank()) {
            String docLimpo = dto.clienteDocumento().replaceAll("\\D", "");
            Cliente clienteEncontrado = clienteRepository.findByDocumento(docLimpo).orElse(null);

            if (clienteEncontrado != null) {
                if (docLimpo.length() == 14) {
                    if (dto.clienteIe() != null) clienteEncontrado.setInscricaoEstadual(dto.clienteIe());
                    if (dto.clienteLogradouro() != null) clienteEncontrado.setLogradouro(dto.clienteLogradouro());
                    if (dto.clienteNumero() != null) clienteEncontrado.setNumero(dto.clienteNumero());
                    if (dto.clienteBairro() != null) clienteEncontrado.setBairro(dto.clienteBairro());
                    if (dto.clienteCidade() != null) clienteEncontrado.setCidade(dto.clienteCidade());
                    if (dto.clienteUf() != null) clienteEncontrado.setUf(dto.clienteUf());
                    if (dto.clienteCep() != null) clienteEncontrado.setCep(dto.clienteCep().replaceAll("\\D", ""));
                    clienteRepository.save(clienteEncontrado);
                }
                venda.setCliente(clienteEncontrado);
                venda.setClienteNome(clienteEncontrado.getNome());
                venda.setClienteDocumento(clienteEncontrado.getDocumento());
                venda.setClienteTelefone(clienteEncontrado.getTelefone());
            } else {
                if (docLimpo.length() == 14) {
                    Cliente novaEmpresa = new Cliente();
                    novaEmpresa.setDocumento(docLimpo);
                    novaEmpresa.setNome(dto.clienteNome() != null && !dto.clienteNome().isBlank() ? dto.clienteNome() : "Empresa Não Identificada");
                    novaEmpresa.setTelefone(dto.clienteTelefone() != null ? dto.clienteTelefone().replaceAll("\\D", "") : null);

                    String ieLimpa = dto.clienteIe() != null ? dto.clienteIe().replaceAll("\\D", "") : "";
                    novaEmpresa.setInscricaoEstadual(ieLimpa.isBlank() ? "ISENTO" : ieLimpa);

                    novaEmpresa.setLogradouro(dto.clienteLogradouro() != null ? dto.clienteLogradouro() : "Nao Informado");
                    novaEmpresa.setNumero(dto.clienteNumero() != null ? dto.clienteNumero() : "SN");
                    novaEmpresa.setBairro(dto.clienteBairro() != null ? dto.clienteBairro() : "Centro");
                    novaEmpresa.setCidade(dto.clienteCidade() != null ? dto.clienteCidade() : "Recife");
                    novaEmpresa.setUf(dto.clienteUf() != null ? dto.clienteUf() : "PE");
                    novaEmpresa.setCep(dto.clienteCep() != null ? dto.clienteCep().replaceAll("\\D", "") : "50000000");

                    novaEmpresa.setAtivo(true);
                    novaEmpresa.setLimiteCredito(BigDecimal.ZERO);
                    clienteEncontrado = clienteRepository.save(novaEmpresa);
                    venda.setCliente(clienteEncontrado);
                }
                venda.setClienteNome(dto.clienteNome() != null && !dto.clienteNome().isBlank() ? dto.clienteNome() : clientePadraoCupom);
                venda.setClienteDocumento(dto.clienteDocumento());
                venda.setClienteTelefone(dto.clienteTelefone());
            }

        } else if (dto.clienteTelefone() != null && !dto.clienteTelefone().isBlank() && dto.clienteNome() != null && !dto.clienteNome().isBlank()) {
            String telLimpo = dto.clienteTelefone().replaceAll("\\D", "");

            String telMascara;
            if (telLimpo.length() == 11) {
                telMascara = String.format("(%s) %s-%s", telLimpo.substring(0, 2), telLimpo.substring(2, 7), telLimpo.substring(7));
            } else {
                telMascara = telLimpo;
            }

            Cliente clienteEncontrado = clienteRepository.findByTelefone(telLimpo)
                    .orElseGet(() -> clienteRepository.findByTelefone(telMascara).orElse(null));

            if (clienteEncontrado != null) {
                clienteEncontrado.setNome(dto.clienteNome().toUpperCase());
                clienteRepository.save(clienteEncontrado);

                venda.setCliente(clienteEncontrado);
                venda.setClienteNome(clienteEncontrado.getNome());
                venda.setClienteTelefone(clienteEncontrado.getTelefone());
            } else {
                // 🔥 CRIAÇÃO LIMPA DE CLIENTE B2C (Apenas Nome + Telefone, Documento = null)
                Cliente novoCliente = new Cliente();
                novoCliente.setNome(dto.clienteNome().toUpperCase());
                novoCliente.setTelefone(telLimpo);
                novoCliente.setDocumento(null); // O BD agora permite nulo!
                novoCliente.setAtivo(true);
                novoCliente.setLimiteCredito(BigDecimal.ZERO);

                novoCliente = clienteRepository.save(novoCliente);

                venda.setCliente(novoCliente);
                venda.setClienteNome(novoCliente.getNome());
                venda.setClienteTelefone(novoCliente.getTelefone());
            }
        } else {
            venda.setClienteNome(dto.clienteNome() != null && !dto.clienteNome().isBlank() ? dto.clienteNome() : clientePadraoCupom);
            venda.setClienteDocumento(dto.clienteDocumento());
            venda.setClienteTelefone(dto.clienteTelefone());
        }

        venda.setDescontoTotal(nvl(dto.descontoTotal()));

        List<Long> idsProdutos = dto.itens().stream().map(ItemVendaDTO::produtoId).collect(Collectors.toList());
        Map<Long, Produto> mapProdutos = produtoRepository.findAllById(idsProdutos).stream()
                .collect(Collectors.toMap(Produto::getId, p -> p));

        final Venda vendaRef = venda;

        List<ItemVenda> itens = dto.itens().stream().map(itemDto -> {
            Produto produto = mapProdutos.get(itemDto.produtoId());
            if (produto == null) throw new ResourceNotFoundException("Produto não encontrado ID: " + itemDto.produtoId());

            processarSaidaEstoqueComAuditoria(produto, itemDto.quantidade().intValue());

            ItemVenda item = new ItemVenda();
            item.setVenda(vendaRef);
            item.setProduto(produto);
            item.setQuantidade(itemDto.quantidade());
            item.setPrecoUnitario(nvl(itemDto.precoUnitario()));
            item.setDesconto(nvl(itemDto.desconto()));
            item.setCustoUnitarioHistorico(nvl(produto.getPrecoCusto()));

            // 🔥 CORREÇÃO: Utilizando a sintaxe de Record (sem 'get')
            if (itemDto.influenciaIA() != null) {
                item.setInfluenciaIA(itemDto.influenciaIA());
            } else {
                item.setInfluenciaIA(TipoInfluenciaIA.NENHUMA);
            }
            return item;
        }).collect(Collectors.toList());

        venda.setItens(itens);

        BigDecimal subtotalItens = itens.stream()
                .map(i -> nvl(i.getPrecoUnitario()).multiply(new BigDecimal(i.getQuantidade().toString())).subtract(nvl(i.getDesconto())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valorFinal = subtotalItens.subtract(nvl(venda.getDescontoTotal())).max(BigDecimal.ZERO);
        venda.setValorTotal(valorFinal);

        BigDecimal totalPago = dto.pagamentos().stream().map(p -> nvl(p.valor())).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPago.compareTo(valorFinal) < 0 && totalPago.subtract(valorFinal).abs().compareTo(new BigDecimal("0.05")) > 0) {
            throw new ValidationException("O valor pago (R$ " + totalPago + ") é insuficiente.");
        }

        venda.setTroco(totalPago.subtract(valorFinal).max(BigDecimal.ZERO));

        List<PagamentoVenda> pagamentos = dto.pagamentos().stream().map(pgDto -> {
            PagamentoVenda pg = new PagamentoVenda();
            pg.setVenda(vendaRef);
            pg.setFormaPagamento(pgDto.formaPagamento());
            pg.setValor(nvl(pgDto.valor()));
            pg.setParcelas(pgDto.parcelas() != null ? pgDto.parcelas() : 1);
            return pg;
        }).collect(Collectors.toList());

        venda.setPagamentos(pagamentos);
        if (!pagamentos.isEmpty()) venda.setFormaDePagamento(pagamentos.get(0).getFormaPagamento());

        BigDecimal totalDescontosConcedidos = venda.getDescontoTotal().add(itens.stream().map(i -> nvl(i.getDesconto())).reduce(BigDecimal.ZERO, BigDecimal::add));

        validarLimitesDeDesconto(usuarioLogado, subtotalItens, totalDescontosConcedidos);

        // Salva a venda como PENDENTE no banco
        venda.setStatusNfce(StatusFiscal.PENDENTE);

        venda = vendaRepository.save(venda);
        final Long idVendaSalva = venda.getIdVenda();

        gerarTitulosFiado(venda, pagamentos);

        try {
            gerarDadosFiscaisNfce(venda, config);
        } catch (Exception e) {
            log.warn("Falha ao gerar chave estática, operando em contingência: {}", e.getMessage());
            venda.setStatusNfce(StatusFiscal.PENDENTE);
            venda.setChaveAcessoNfce("AguardandoSefaz");
        }

        venda = vendaRepository.save(venda);
        atualizarFinanceiro(caixa, venda, pagamentos);

        if (dto.logAuditoria() != null && !dto.logAuditoria().isEmpty()) {
            for (VendaRequestDTO.LogAuditoriaPDVDTO logPDV : dto.logAuditoria()) {
                String mensagemFormatada = String.format("[Ação: %s] %s | Marcador: %s | Venda ID: %d",
                        logPDV.acao(), logPDV.detalhes(), logPDV.hora(), idVendaSalva);
                auditoriaService.registrarAcao("ALERTA_PDV", usuarioLogado.getNome(), mensagemFormatada);
            }
        }

        if (!Boolean.TRUE.equals(dto.ehOrcamento())) {
            final String tipoNota = dto.tipoNota() != null ? dto.tipoNota() : "NFCE";

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CompletableFuture.runAsync(() -> {
                        try {
                            if ("NFE".equals(tipoNota)) {
                                log.info("🔥 B2B Detectado! Emitindo NF-e Modelo 55 em background para a Venda {}", idVendaSalva);
                                nfeService.emitirNfeModelo55(idVendaSalva);
                            } else {
                                log.info("🛒 B2C Detectado! Emitindo NFC-e Modelo 65 em background para a Venda {}", idVendaSalva);
                                Venda vParaEmitir = vendaRepository.findByIdComItens(idVendaSalva).orElse(null);
                                if(vParaEmitir != null) nfceService.emitirNfce(vParaEmitir);
                            }
                        } catch (ValidationException ve) {
                            log.error("SEFAZ Rejeitou a nota {}: {}", idVendaSalva, ve.getMessage());
                            Venda vRejeitada = vendaRepository.findById(idVendaSalva).orElse(null);
                            if(vRejeitada != null){
                                vRejeitada.setStatusNfce(StatusFiscal.REJEITADA);
                                vRejeitada.setMensagemRejeicao(ve.getMessage());
                                vendaRepository.save(vRejeitada);
                            }
                        } catch (Exception e) {
                            log.error("Erro na comunicação com a SEFAZ para a venda {}: {}", idVendaSalva, e.getMessage());
                            Venda vRejeitada = vendaRepository.findById(idVendaSalva).orElse(null);
                            if(vRejeitada != null){
                                vRejeitada.setStatusNfce(StatusFiscal.REJEITADA);
                                vRejeitada.setMensagemRejeicao("Erro interno na emissão: " + e.getMessage());
                                vendaRepository.save(vRejeitada);
                            }
                        }
                    });
                }
            });
        }

        return new VendaResponseDTO(venda);
    }

    private void gerarDadosFiscaisNfce(Venda venda, ConfiguracaoLoja config) {
        try {
            String cnpjStr = "00000000000000";
            if (config != null && config.getLoja() != null && config.getLoja().getCnpj() != null) {
                String cleanCnpj = config.getLoja().getCnpj().replaceAll("\\D", "");
                if (cleanCnpj.length() == 14) cnpjStr = cleanCnpj;
            }

            String uf = "26"; // PE
            LocalDateTime data = venda.getDataVenda() != null ? venda.getDataVenda() : LocalDateTime.now();
            String anoMes = String.format("%02d%02d", data.getYear() % 100, data.getMonthValue());
            String modelo = "65"; // NFC-e

            boolean isProducao = config != null && config.getFiscal() != null && "PRODUCAO".equals(config.getFiscal().getAmbiente());

            Integer serieConfig = 1;
            if (config != null && config.getFiscal() != null) {
                serieConfig = isProducao ? config.getFiscal().getSerieProducao() : config.getFiscal().getSerieHomologacao();
            }
            if (serieConfig == null) serieConfig = 1;

            String serie = String.format("%03d", serieConfig);

            Long idVendaSeguro = venda.getIdVenda() != null ? venda.getIdVenda() :
                    (venda.getIdVenda() != null ? venda.getIdVenda() : (long) (new Random().nextInt(999999) + 1));

            String numeroNfce = String.format("%09d", idVendaSeguro);
            venda.setNumeroNfce(idVendaSeguro);
            venda.setSerieNfce(serieConfig);

            String tipoEmissao = "1"; // 1 = Normal
            String cNF = String.format("%08d", new Random().nextInt(99999999));

            String chaveSemDV = uf + anoMes + cnpjStr + modelo + serie + numeroNfce + tipoEmissao + cNF;
            String dv = calcularDigitoVerificadorModulo11(chaveSemDV);

            venda.setChaveAcessoNfce(chaveSemDV + dv);
            venda.setStatusNfce(StatusFiscal.AUTORIZADA); // Fica pre-autorizada até a thread background validar

            String ambiente = isProducao ? "1" : "2";
            venda.setProtocolo(ambiente + uf + String.format("%012d", System.currentTimeMillis() % 1000000000000L));

        } catch (Exception e) {
            log.error("FALHA GRAVE AO GERAR CHAVE FISCAL: {}", e.getMessage());
            venda.setStatusNfce(StatusFiscal.PENDENTE);
            venda.setChaveAcessoNfce("AguardandoSefaz");
        }
    }

    private String calcularDigitoVerificadorModulo11(String chave43) {
        int soma = 0;
        int peso = 2;
        for (int i = 42; i >= 0; i--) {
            soma += Character.getNumericValue(chave43.charAt(i)) * peso;
            peso++;
            if (peso > 9) peso = 2;
        }
        int resto = soma % 11;
        int dv = 11 - resto;
        if (dv == 10 || dv == 11) dv = 0;
        return String.valueOf(dv);
    }

    @Transactional(rollbackFor = Exception.class)
    public Venda suspenderVenda(VendaRequestDTO dto) {
        Usuario usuario = capturarUsuarioLogado();
        CaixaDiario caixa = validarCaixaAberto(usuario);

        Venda venda = new Venda();
        venda.setUsuario(usuario);
        venda.setCaixa(caixa);
        venda.setClienteNome(dto.clienteNome() != null ? dto.clienteNome() : "Venda Suspensa");
        venda.setDataVenda(LocalDateTime.now());
        venda.setStatusNfce(StatusFiscal.PENDENTE);

        if (dto.clienteId() != null) {
            clienteRepository.findById(dto.clienteId()).ifPresent(venda::setCliente);
        } else {
            venda.setClienteDocumento(dto.clienteDocumento());
            venda.setClienteTelefone(dto.clienteTelefone());
        }

        if (dto.pagamentos() != null && !dto.pagamentos().isEmpty()) {
            venda.setFormaDePagamento(dto.pagamentos().get(0).formaPagamento());
        } else {
            venda.setFormaDePagamento(FormaDePagamento.DINHEIRO);
        }

        BigDecimal totalItens = processarItensParaOrcamento(venda, dto.itens());
        BigDecimal descontoAplicado = nvl(dto.descontoTotal());
        venda.setDescontoTotal(descontoAplicado);
        venda.setValorTotal(totalItens.subtract(descontoAplicado).max(BigDecimal.ZERO));
        venda.setObservacao(dto.observacao());

        return vendaRepository.save(venda);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelarVenda(Long idVenda, String motivo) {
        Venda venda = buscarVendaComItens(idVenda);
        if (venda.getStatusNfce() == StatusFiscal.CANCELADA) throw new ValidationException("Esta venda já está cancelada.");

        venda.getItens().forEach(item -> {
            AjusteEstoqueDTO ajuste = new AjusteEstoqueDTO();
            ajuste.setCodigoBarras(item.getProduto().getCodigoBarras());
            ajuste.setQuantidade(item.getQuantidade());
            ajuste.setMotivo(MotivoMovimentacaoDeEstoque.CANCELAMENTO_DE_VENDA);
            estoqueService.realizarAjusteManual(ajuste);
        });

        try {
            financeiroService.cancelarReceitaDeVenda(idVenda);
        } catch (Exception e) {
            log.warn("Nenhum registro financeiro encontrado para cancelar na venda {}", idVenda);
        }

        venda.setStatusNfce(StatusFiscal.CANCELADA);
        venda.setMotivoDoCancelamento(motivo);
        vendaRepository.save(venda);

        Usuario op = capturarUsuarioLogado();
        auditoriaService.registrarAcao("CANCELAMENTO_VENDA", op != null ? op.getNome() : "Sistema", "Venda #" + idVenda + " cancelada. Motivo: " + motivo);
    }

    @Transactional(rollbackFor = Exception.class)
    public Venda efetivarVenda(Long idVendaPrevia) {
        Venda venda = buscarVendaComItens(idVendaPrevia);
        if (venda.getStatusNfce() != StatusFiscal.PENDENTE) {
            throw new ValidationException("Apenas vendas suspensas (Pendentes) podem ser efetivadas.");
        }

        Usuario operador = capturarUsuarioLogado();
        CaixaDiario caixa = validarCaixaAberto(operador);

        venda.setDataVenda(LocalDateTime.now());

        if (venda.getFormaDePagamento() == FormaDePagamento.CREDIARIO) {
            validarCreditoDoCliente(venda.getClienteDocumento(), venda.getValorTotal());
        }

        venda.getItens().forEach(item -> estoqueService.registrarSaidaVenda(item.getProduto(), item.getQuantidade().intValue()));

        financeiroService.lancarReceitaDeVenda(venda.getIdVenda(), venda.getValorTotal(), venda.getFormaDePagamento().name(), venda.getQuantidadeParcelas(), buscarIdClientePorDocumento(venda.getClienteDocumento()));

        gerarTitulosFiado(venda, venda.getPagamentos());
        atualizarFinanceiro(caixa, venda, venda.getPagamentos());

        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
        try {
            gerarDadosFiscaisNfce(venda, config);
            vendaRepository.save(venda);
        } catch (Exception e) { log.warn("Aviso chave na retomada: {}", e.getMessage()); }

        final Long idVendaTransmitir = venda.getIdVenda();
        final String docCli = venda.getClienteDocumento() != null ? venda.getClienteDocumento().replaceAll("\\D", "") : "";

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                CompletableFuture.runAsync(() -> {
                    try {
                        if (docCli.length() == 14) {
                            nfeService.emitirNfeModelo55(idVendaTransmitir);
                        } else {
                            Venda vParaEmitir = vendaRepository.findByIdComItens(idVendaTransmitir).orElse(null);
                            if(vParaEmitir != null) nfceService.emitirNfce(vParaEmitir);
                        }
                    } catch (ValidationException ve) {
                        log.error("SEFAZ Rejeitou a nota retomada {}: {}", idVendaTransmitir, ve.getMessage());
                        Venda vRejeitada = vendaRepository.findById(idVendaTransmitir).orElse(null);
                        if(vRejeitada != null){
                            vRejeitada.setStatusNfce(StatusFiscal.REJEITADA);
                            vRejeitada.setMensagemRejeicao(ve.getMessage());
                            vendaRepository.save(vRejeitada);
                        }
                    } catch (Exception e) {
                        log.error("Erro na emissão fiscal retomada da venda {}: {}", idVendaTransmitir, e.getMessage());
                    }
                });
            }
        });

        return venda;
    }

    @Transactional(readOnly = true)
    public List<VendaResponseDTO> listarVendasSuspensas() {
        return vendaRepository.findByStatusNfce(StatusFiscal.PENDENTE).stream().map(VendaResponseDTO::new).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<VendaResponseDTO> listarVendas(LocalDate inicio, LocalDate fim, Pageable pageable) {
        LocalDateTime dataInicio = (inicio != null) ? inicio.atStartOfDay() : LocalDateTime.now().minusDays(30);
        LocalDateTime dataFim = (fim != null) ? fim.atTime(LocalTime.MAX) : LocalDateTime.now();
        return vendaRepository.findByDataVendaBetween(dataInicio, dataFim, pageable).map(VendaResponseDTO::new);
    }

    @Transactional(readOnly = true)
    public Venda buscarVendaComItens(Long id) {
        Venda venda = vendaRepository.findByIdComItens(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venda #" + id + " não encontrada."));

        Hibernate.initialize(venda.getPagamentos());
        Hibernate.initialize(venda.getUsuario());

        return venda;
    }

    private void atualizarFinanceiro(CaixaDiario caixa, Venda venda, List<PagamentoVenda> pagamentos) {
        if (pagamentos == null || pagamentos.isEmpty()) return;
        BigDecimal trocoRestante = nvl(venda.getTroco());

        for (PagamentoVenda pg : pagamentos) {
            BigDecimal valorReal = nvl(pg.getValor());

            if (pg.getFormaPagamento() == FormaDePagamento.DINHEIRO && trocoRestante.compareTo(BigDecimal.ZERO) > 0) {
                if (valorReal.compareTo(trocoRestante) >= 0) {
                    valorReal = valorReal.subtract(trocoRestante);
                    trocoRestante = BigDecimal.ZERO;
                } else {
                    trocoRestante = trocoRestante.subtract(valorReal);
                    valorReal = BigDecimal.ZERO;
                }
            }

            if (valorReal.compareTo(BigDecimal.ZERO) > 0) {
                switch (pg.getFormaPagamento()) {
                    case DINHEIRO -> {
                        caixa.setTotalVendasDinheiro(nvl(caixa.getTotalVendasDinheiro()).add(valorReal));
                        caixa.setSaldoAtual(nvl(caixa.getSaldoAtual()).add(valorReal));
                    }
                    case PIX -> caixa.setTotalVendasPix(nvl(caixa.getTotalVendasPix()).add(valorReal));
                    case CREDITO, CARTAO_CREDITO -> {
                        caixa.setTotalVendasCredito(nvl(caixa.getTotalVendasCredito()).add(valorReal));
                        caixa.setTotalVendasCartao(nvl(caixa.getTotalVendasCartao()).add(valorReal));
                    }
                    case DEBITO, CARTAO_DEBITO -> {
                        caixa.setTotalVendasDebito(nvl(caixa.getTotalVendasDebito()).add(valorReal));
                        caixa.setTotalVendasCartao(nvl(caixa.getTotalVendasCartao()).add(valorReal));
                    }
                    case CREDIARIO -> {
                        log.info("Venda registrada no Crediário/Fiado: R$ {}", valorReal);
                    }
                    default -> log.warn("Forma não mapeada no caixa: {}", pg.getFormaPagamento());
                }
            }
        }
        caixaRepository.save(caixa);
    }

    private void gerarTitulosFiado(Venda vendaSalva, List<PagamentoVenda> pagamentos) {
        if (pagamentos == null) return;
        for (PagamentoVenda pg : pagamentos) {
            if (FormaDePagamento.CREDIARIO.equals(pg.getFormaPagamento())) {
                if (vendaSalva.getCliente() == null) {
                    throw new ValidationException("Para vendas no fiado (crediário), o cliente deve estar cadastrado/identificado.");
                }
                TituloReceber titulo = new TituloReceber();
                titulo.setCliente(vendaSalva.getCliente());
                titulo.setVendaId(vendaSalva.getIdVenda());
                titulo.setDescricao("Venda PDV #" + vendaSalva.getIdVenda());
                titulo.setDataCompra(LocalDate.now());
                titulo.setDataVencimento(LocalDate.now().plusDays(30)); // 30 dias de prazo padrão
                titulo.setValorTotal(pg.getValor());
                titulo.setSaldoDevedor(pg.getValor());
                titulo.setValorPago(BigDecimal.ZERO);
                titulo.setStatus(StatusTitulo.PENDENTE);

                tituloReceberRepository.save(titulo);
            }
        }
    }

    private BigDecimal nvl(BigDecimal val) { return val == null ? BigDecimal.ZERO : val; }
    private BigDecimal nvl(BigDecimal val, BigDecimal padrao) { return val == null ? padrao : val; }

    private void processarSaidaEstoqueComAuditoria(Produto produto, int qtdVenda) {
        if (produto.getQuantidadeEmEstoque() < qtdVenda) {
            Usuario op = capturarUsuarioLogado();
            auditoriaService.registrarAcao("ESTOQUE_NEGATIVO", op != null ? op.getNome() : "Sistema",
                    String.format("Venda sem estoque suficiente: %s. Vendido: %d, Tinha: %d", produto.getQuantidadeEmEstoque(), qtdVenda, produto.getQuantidadeEmEstoque()));
        }
        estoqueService.registrarSaidaVenda(produto, qtdVenda);
    }

    private BigDecimal processarItensParaOrcamento(Venda venda, List<ItemVendaDTO> dtos) {
        BigDecimal total = BigDecimal.ZERO;
        List<Long> idsProdutos = dtos.stream().map(ItemVendaDTO::produtoId).collect(Collectors.toList());
        Map<Long, Produto> mapProdutos = produtoRepository.findAllById(idsProdutos).stream().collect(Collectors.toMap(Produto::getId, p -> p));

        for (ItemVendaDTO dto : dtos) {
            Produto p = mapProdutos.get(dto.produtoId());
            if (p == null) throw new ResourceNotFoundException("Produto não encontrado: " + dto.produtoId());

            ItemVenda i = new ItemVenda();
            i.setVenda(venda);
            i.setProduto(p);
            i.setQuantidade(dto.quantidade());
            i.setPrecoUnitario(nvl(dto.precoUnitario()));
            i.setDesconto(nvl(dto.desconto()));

            // 🔥 CORREÇÃO: Registando a IA também nas vendas suspensas (Orçamentos)
            if (dto.influenciaIA() != null) {
                i.setInfluenciaIA(dto.influenciaIA());
            } else {
                i.setInfluenciaIA(TipoInfluenciaIA.NENHUMA);
            }

            if (venda.getItens() == null) venda.setItens(new ArrayList<>());
            venda.getItens().add(i);
            total = total.add(i.getPrecoUnitario().multiply(new BigDecimal(String.valueOf(i.getQuantidade()))).subtract(i.getDesconto()));
        }
        return total;
    }

    private void validarLimitesDeDesconto(Usuario usuario, BigDecimal totalVenda, BigDecimal descontoAplicado) {
        if (descontoAplicado.compareTo(BigDecimal.ZERO) <= 0) return;
        ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
        ConfiguracaoLoja.DadosFinanceiro dadosFin = config != null ? config.getFinanceiro() : null;

        if (dadosFin == null) {
            dadosFin = new ConfiguracaoLoja.DadosFinanceiro();
            dadosFin.setDescCaixa(new BigDecimal("5.00"));
            dadosFin.setDescGerente(new BigDecimal("20.00"));
        }

        BigDecimal bruto = totalVenda.add(descontoAplicado);
        if (bruto.compareTo(BigDecimal.ZERO) == 0) return;

        BigDecimal percentual = descontoAplicado.divide(bruto, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        BigDecimal limite = (usuario.getPerfilDoUsuario() == PerfilDoUsuario.ROLE_ADMIN) ? nvl(dadosFin.getDescGerente(), new BigDecimal("20.00")) : nvl(dadosFin.getDescCaixa(), new BigDecimal("5.00"));

        if (percentual.compareTo(limite) > 0) {
            throw new ValidationException("Desconto de " + percentual.setScale(2, RoundingMode.HALF_UP) + "% excede o limite permitido de " + limite + "%");
        }
    }

    private Usuario capturarUsuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) return null;
        return usuarioRepository.findByMatriculaOrEmail(auth.getName(), auth.getName()).orElse(null);
    }

    private CaixaDiario validarCaixaAberto(Usuario usuario) {
        return caixaRepository.findFirstByUsuarioAberturaAndStatus(usuario, StatusCaixa.ABERTO)
                .orElseThrow(() -> new ValidationException("O seu caixa está FECHADO. Abra o caixa para operar."));
    }

    private Long buscarIdClientePorDocumento(String doc) {
        if (doc == null) return null;
        return clienteRepository.findByDocumento(doc.replaceAll("\\D", "")).map(Cliente::getId).orElse(null);
    }

    private void validarCreditoDoCliente(String documento, BigDecimal valor) {
        if (documento == null) throw new ValidationException("Documento obrigatório para Crediário.");
        Cliente cliente = clienteRepository.findByDocumento(documento.replaceAll("\\D", "")).orElseThrow(() -> new ResourceNotFoundException("Cliente não cadastrado."));
        BigDecimal divida = nvl(contaReceberRepository.somarDividaTotalPorDocumento(cliente.getDocumento()));
        if (divida.add(valor).compareTo(cliente.getLimiteCredito()) > 0) throw new ValidationException("Limite de crédito excedido!");
    }

    // =========================================================================
    // MÉTODOS AUXILIARES DE E-MAIL E PDF
    // =========================================================================
    private JavaMailSenderImpl configurarMailSender(ConfiguracaoLoja config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(config.getSmtpHost() != null ? config.getSmtpHost() : "smtp.gmail.com");
        mailSender.setPort(config.getSmtpPort() != null ? config.getSmtpPort() : 587);
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return mailSender;
    }

    private String gerarCorpoEmail(Venda venda, ConfiguracaoLoja config) {
        StringBuilder corpo = new StringBuilder();
        corpo.append("Olá, ").append(venda.getClienteNome() != null ? venda.getClienteNome() : "Cliente").append("!\n\n");
        corpo.append("Agradecemos a sua compra via ").append(venda.getFormaDePagamento()).append(".\n");
        corpo.append("Data da transação: ").append(venda.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("\n\n");

        corpo.append("📦 ITENS DO SEU PEDIDO:\n");
        corpo.append("------------------------------------------\n");
        for (ItemVenda item : venda.getItens()) {
            corpo.append(String.format("- %s x %s: R$ %.2f\n",
                    item.getQuantidade().setScale(0, RoundingMode.DOWN),
                    item.getProduto().getDescricao(),
                    item.getPrecoUnitario()));
        }
        corpo.append("------------------------------------------\n");
        corpo.append(String.format("VALOR TOTAL: R$ %.2f\n", venda.getValorTotal()));
        corpo.append("------------------------------------------\n\n");
        corpo.append("Anexamos a este e-mail o seu documento fiscal.\n\nAtenciosamente,\n")
                .append(config.getLoja().getNomeFantasia());

        return corpo.toString();
    }

    private boolean isB2B(Venda venda) {
        String docLimpo = venda.getClienteDocumento() != null ? venda.getClienteDocumento().replaceAll("\\D", "") : "";
        return docLimpo.length() == 14;
    }

    private void aguardarXmlSefaz(Venda venda) {
        int tentativas = 0;
        while ((venda.getXmlNota() == null || venda.getXmlNota().isBlank()) && tentativas < 5) {
            try {
                Thread.sleep(1000);
                Venda atualizada = vendaRepository.findByIdComItens(venda.getIdVenda()).orElse(null);
                if (atualizada != null) venda.setXmlNota(atualizada.getXmlNota());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            tentativas++;
        }
    }

    private void anexarXmlSeExistir(Venda venda, MimeMessageHelper helper, Long idVenda) throws Exception {
        if (venda.getXmlNota() != null && !venda.getXmlNota().isBlank()) {
            ByteArrayResource xml = new ByteArrayResource(venda.getXmlNota().getBytes(StandardCharsets.UTF_8));
            helper.addAttachment("XML_NFe_" + idVenda + ".xml", xml);
        }
    }

    private void criarEAnexarPdfEspelho(Venda venda, MimeMessageHelper helper, Long idVenda) throws Exception {
        helper.addAttachment("Recibo_Auxiliar_" + idVenda + ".pdf", gerarPdfEspelho(venda));
    }

    @Transactional(readOnly = true)
    public void enviarEmailComXmlEPdf(Long idVenda, String emailDestino) {
        Venda venda = vendaRepository.findByIdComItens(idVenda)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada: " + idVenda));

        boolean isB2B = isB2B(venda);
        aguardarXmlSefaz(venda);

        try {
            ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
            JavaMailSenderImpl mailSender = configurarMailSender(config);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(config.getSmtpUsername() != null ? config.getSmtpUsername() : "naoresponda@ddcosmeticos.com.br");
            helper.setTo(emailDestino);
            helper.setSubject("Documento Fiscal - " + config.getLoja().getNomeFantasia() + " (Venda #" + idVenda + ")");
            helper.setText(gerarCorpoEmail(venda, config));

            if (isB2B) {
                anexarXmlSeExistir(venda, helper, idVenda);
                try {
                    byte[] pdfDanfe = impressaoService.gerarDanfeA4(venda.getIdVenda());
                    helper.addAttachment("DANFE_" + idVenda + ".pdf", new ByteArrayResource(pdfDanfe));
                } catch (Exception e) {
                    log.warn("Gerador DANFE A4 falhou, anexando espelho auxiliar.", e);
                    criarEAnexarPdfEspelho(venda, helper, idVenda);
                }
            } else {
                try {
                    byte[] pdfCupom = impressaoService.gerarCupomNfce(venda.getIdVenda());
                    helper.addAttachment("CupomFiscal_" + idVenda + ".pdf", new ByteArrayResource(pdfCupom));
                } catch (Exception e) {
                    log.warn("Gerador Cupom Bobina falhou, anexando espelho auxiliar.", e);
                    criarEAnexarPdfEspelho(venda, helper, idVenda);
                }
            }

            mailSender.send(message);
            log.info("E-mail com PDF nativo ({}) enviado com sucesso para: {}", isB2B ? "B2B" : "B2C", emailDestino);

        } catch (Exception e) {
            log.error("Erro ao enviar e-mail da venda {}: {}", idVenda, e.getMessage());
            throw new ValidationException("Falha ao enviar e-mail fiscal. Verifique a conexão com o provedor SMTP.");
        }
    }

    @Transactional(readOnly = true)
    public void enviarEmailComDocumentoFrontend(Long idVenda, String emailDestino, MultipartFile pdfFrontend) {
        Venda venda = vendaRepository.findByIdComItens(idVenda)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada: " + idVenda));

        boolean isB2B = isB2B(venda);
        aguardarXmlSefaz(venda);

        try {
            ConfiguracaoLoja config = configuracaoLojaService.buscarConfiguracao();
            JavaMailSenderImpl mailSender = configurarMailSender(config);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(config.getSmtpUsername() != null ? config.getSmtpUsername() : "naoresponda@ddcosmeticos.com.br");
            helper.setTo(emailDestino);
            helper.setSubject("Documento Fiscal - " + config.getLoja().getNomeFantasia() + " (Venda #" + idVenda + ")");
            helper.setText(gerarCorpoEmail(venda, config));

            String nomePdf = isB2B ? "DANFE_" + idVenda + ".pdf" : "CupomFiscal_" + idVenda + ".pdf";
            helper.addAttachment(nomePdf, new ByteArrayResource(pdfFrontend.getBytes()));

            if (isB2B) {
                anexarXmlSeExistir(venda, helper, idVenda);
            }

            mailSender.send(message);
            log.info("E-mail com PDF do React enviado com sucesso para: {}", emailDestino);

        } catch (Exception e) {
            log.error("Erro ao enviar e-mail (Frontend) da venda {}: {}", idVenda, e.getMessage());
            throw new ValidationException("Falha ao enviar e-mail fiscal. Verifique a conexão com o provedor SMTP.");
        }
    }

    private ByteArrayResource gerarPdfEspelho(Venda venda) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4.rotate());

        try {
            com.lowagie.text.pdf.PdfWriter.getInstance(document, out);
            document.open();

            com.lowagie.text.Font titleFont = com.lowagie.text.FontFactory.getFont(com.lowagie.text.FontFactory.HELVETICA_BOLD, 16);
            com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("Documento Auxiliar de Venda - DD Cosméticos", titleFont);
            title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            document.add(title);

            document.add(new com.lowagie.text.Paragraph("\nNúmero da Venda: " + venda.getIdVenda()));
            document.add(new com.lowagie.text.Paragraph("Data e Hora: " + venda.getDataVenda().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))));
            document.add(new com.lowagie.text.Paragraph("Cliente: " + (venda.getClienteNome() != null ? venda.getClienteNome() : "Consumidor Final")));
            document.add(new com.lowagie.text.Paragraph("Valor Total Pago: R$ " + venda.getValorTotal()));

            String chave = venda.getChaveAcessoNfce() != null && !venda.getChaveAcessoNfce().isBlank() ? venda.getChaveAcessoNfce() : "Aguardando processamento SEFAZ";
            document.add(new com.lowagie.text.Paragraph("Chave de Acesso: " + chave));

            document.add(new com.lowagie.text.Paragraph("\n\n* O arquivo XML oficial encontra-se em anexo neste e-mail (se aprovado)."));
            document.close();

        } catch (com.lowagie.text.DocumentException e) {
            log.error("Erro gerando PDF espelho da venda {}", venda.getIdVenda(), e);
        }

        return new ByteArrayResource(out.toByteArray());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NfceResponseDTO tentarEmissaoSincrona(Long idVenda) {
        Venda venda = buscarVendaComItens(idVenda);
        String docCli = venda.getClienteDocumento() != null ? venda.getClienteDocumento().replaceAll("\\D", "") : "";

        try {
            if (docCli.length() == 14) {
                return nfeService.emitirNfeModelo55(idVenda); // Tenta NF-e
            } else {
                return nfceService.emitirNfce(venda); // Tenta NFC-e
            }
        } catch (ValidationException e) {
            venda.setStatusNfce(StatusFiscal.REJEITADA);
            venda.setMensagemRejeicao(e.getMessage());
            vendaRepository.save(venda);
            throw new ValidationException("SEFAZ Rejeitou: " + e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NfceResponseDTO forcarEmissaoNfce(Long idVenda) {
        Venda venda = buscarVendaComItens(idVenda);

        try {
            log.info("Operador forçou NFC-e para a Venda B2B #{}", idVenda);
            return nfceService.emitirNfce(venda);
        } catch (Exception e) {
            throw new ValidationException("Erro ao forçar NFC-e: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<HistoricoCompraDTO> buscarHistoricoPorCliente(Long idCliente) {
        List<Venda> vendas = vendaRepository.findByClienteIdOrderByDataVendaDesc(idCliente);
        return vendas.stream().map(HistoricoCompraDTO::new).toList();
    }

    @Transactional(readOnly = true)
    public byte[] gerarPdfVenda(Long id) {
        Venda venda = buscarVendaComItens(id);

        if (venda.getXmlNota() == null || venda.getXmlNota().isBlank()) {
            throw new ValidationException("Esta venda ainda não possui nota fiscal emitida e validada.");
        }

        try {
            if (isB2B(venda)) {
                return impressaoService.gerarDanfeA4(venda.getIdVenda());
            } else {
                return impressaoService.gerarCupomNfce(venda.getIdVenda());
            }
        } catch (Exception e) {
            log.error("Erro ao gerar PDF da venda {}", id, e);
            throw new ValidationException("Erro interno ao gerar o documento fiscal: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] obterXmlVenda(Long id) {
        Venda venda = buscarVendaComItens(id);

        if (venda.getXmlNota() == null || venda.getXmlNota().isBlank()) {
            throw new ValidationException("O XML desta venda não está disponível ou a nota não foi autorizada.");
        }

        return venda.getXmlNota().getBytes(StandardCharsets.UTF_8);
    }
}