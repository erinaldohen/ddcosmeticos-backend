package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.exception.ResourceNotFoundException;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.*;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EstoqueService {

    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private MovimentoEstoqueRepository movimentoRepository;
    @Autowired private ContaPagarRepository contaPagarRepository;
    @Autowired private FornecedorRepository fornecedorRepository;
    @Autowired private FornecedorService fornecedorService;
    @Autowired private ProdutoFornecedorRepository produtoFornecedorRepository;

    @Transactional(readOnly = true)
    public List<Produto> gerarSugestaoCompras() {
        return produtoRepository.findProdutosComBaixoEstoque();
    }

    // --- ENTRADA VIA PEDIDO DE COMPRA ---
    @Transactional
    public void processarEntradaDePedido(Produto produto, BigDecimal quantidade, BigDecimal custoUnitarioNota,
                                         Fornecedor fornecedor, String numeroNotaFiscal) {
        BigDecimal qtdEntrada = quantidade;
        BigDecimal novoPrecoMedio = calcularNovoPrecoMedio(produto, qtdEntrada, custoUnitarioNota);

        produto.setPrecoMedioPonderado(novoPrecoMedio);
        produto.setPrecoCusto(novoPrecoMedio);
        produto.setEstoqueFiscal(produto.getEstoqueFiscal() + qtdEntrada.intValue());
        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        registrarMovimentacao(produto, qtdEntrada, custoUnitarioNota, TipoMovimentoEstoque.ENTRADA,
                MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL, "Recebimento Pedido | NF: " + numeroNotaFiscal,
                numeroNotaFiscal, fornecedor);
    }

    // --- ENTRADA MANUAL INTELIGENTE ---
    @Transactional
    public void registrarEntrada(EstoqueRequestDTO dados) {
        Produto produto;

        // CENÁRIO 1: O Frontend mandou um ID de produto existente
        if (dados.getIdProduto() != null) {
            produto = produtoRepository.findById(dados.getIdProduto())
                    .orElseThrow(() -> new ResourceNotFoundException("Produto informado não existe ID: " + dados.getIdProduto()));

            if (dados.getFornecedorId() != null && dados.getCodigoNoFornecedor() != null) {
                salvarVinculoSeNaoExistir(dados.getFornecedorId(), produto, dados.getCodigoNoFornecedor());
            }
        }
        // CENÁRIO 2: Busca por EAN (Fallback)
        else {
            produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                    .orElseGet(() -> {
                        // CENÁRIO 3: Produto realmente novo
                        Produto novo = criarProdutoAutomatico(dados);
                        if (dados.getFornecedorId() != null && dados.getCodigoNoFornecedor() != null) {
                            salvarVinculoSeNaoExistir(dados.getFornecedorId(), novo, dados.getCodigoNoFornecedor());
                        }
                        return novo;
                    });
        }

        BigDecimal qtdEntrada = dados.getQuantidade();
        BigDecimal custoUnitario = dados.getPrecoCusto();

        BigDecimal novoPrecoMedio = calcularNovoPrecoMedio(produto, qtdEntrada, custoUnitario);
        produto.setPrecoMedioPonderado(novoPrecoMedio);
        produto.setPrecoCusto(novoPrecoMedio);

        if (dados.getNumeroNotaFiscal() != null && !dados.getNumeroNotaFiscal().isBlank()) {
            produto.setEstoqueFiscal(produto.getEstoqueFiscal() + qtdEntrada.intValue());
        } else {
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() + qtdEntrada.intValue());
        }
        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        MotivoMovimentacaoDeEstoque motivo = dados.getNumeroNotaFiscal() != null ?
                MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL : MotivoMovimentacaoDeEstoque.COMPRA_SEM_NOTA_FISCAL;

        registrarMovimentacao(produto, qtdEntrada, custoUnitario, TipoMovimentoEstoque.ENTRADA, motivo,
                "NF: " + (dados.getNumeroNotaFiscal() != null ? dados.getNumeroNotaFiscal() : "S/N"),
                dados.getNumeroNotaFiscal(), null);

        // --- LINHA 108 ATUALIZADA ---
        gerarFinanceiroBatch(dados, custoUnitario, qtdEntrada.intValue());
    }

    // --- LEITURA DE XML COM INTELIGÊNCIA ---
    public RetornoImportacaoXmlDTO processarXmlNotaFiscal(MultipartFile arquivo) {
        RetornoImportacaoXmlDTO retorno = new RetornoImportacaoXmlDTO();

        try {
            InputStream is = arquivo.getInputStream();
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            // 1. DADOS DA NOTA
            String numeroNota = null;
            NodeList nListIde = doc.getElementsByTagName("ide");
            if (nListIde.getLength() > 0) {
                numeroNota = getElementValue((Element) nListIde.item(0), "nNF");
                retorno.setNumeroNota(numeroNota);
            }

            // 2. FORNECEDOR
            Fornecedor fornecedorDoXml = null;
            NodeList nListEmit = doc.getElementsByTagName("emit");
            if (nListEmit.getLength() > 0) {
                Element emit = (Element) nListEmit.item(0);
                String cnpjRaw = getElementValue(emit, "CNPJ");

                if (cnpjRaw != null) {
                    String cnpjLimpo = cnpjRaw.replaceAll("[^0-9]", "");

                    // Validação de Duplicidade
                    Optional<Fornecedor> existente = fornecedorRepository.findByCnpj(cnpjLimpo);
                    if (existente.isPresent() && numeroNota != null) {
                        boolean duplicada = movimentoRepository.existsByDocumentoReferenciaAndFornecedorAndTipoMovimentoEstoque(
                                numeroNota, existente.get(), TipoMovimentoEstoque.ENTRADA);

                        if (duplicada) {
                            throw new IllegalArgumentException("A Nota Fiscal " + numeroNota + " já foi importada anteriormente.");
                        }
                    }

                    fornecedorDoXml = fornecedorService.buscarOuCriarRapido(cnpjLimpo);
                    atualizarDadosFornecedorPeloXml(emit, fornecedorDoXml);
                    retorno.setFornecedorId(fornecedorDoXml.getId());
                    retorno.setNomeFornecedor(fornecedorDoXml.getRazaoSocial());
                    retorno.setRazaoSocialFornecedor(fornecedorDoXml.getRazaoSocial());
                }
            }

            // 3. ITENS DA NOTA
            NodeList nListDet = doc.getElementsByTagName("det");
            for (int i = 0; i < nListDet.getLength(); i++) {
                Element det = (Element) nListDet.item(i);
                Element prod = (Element) det.getElementsByTagName("prod").item(0);

                RetornoImportacaoXmlDTO.ItemXmlDTO item = new RetornoImportacaoXmlDTO.ItemXmlDTO();

                String codXml = getElementValue(prod, "cProd");
                String eanXml = getElementValue(prod, "cEAN");
                String descXml = getElementValue(prod, "xProd");
                String ncmXml = getElementValue(prod, "NCM");

                item.setCodigoNoFornecedor(codXml);
                item.setCodigoBarras(isValidEAN(eanXml) ? eanXml : codXml);
                item.setDescricao(descXml);
                item.setQuantidade(new BigDecimal(getElementValue(prod, "qCom")));
                item.setPrecoCusto(new BigDecimal(getElementValue(prod, "vUnCom")));
                item.setTotal(new BigDecimal(getElementValue(prod, "vProd")));
                item.setNcm(ncmXml);
                item.setUnidade(getElementValue(prod, "uCom"));

                // Lógica de Match
                Produto produtoEncontrado = null;
                StatusMatch status = StatusMatch.NOVO_PRODUTO;
                String motivo = "Novo Produto";

                if (fornecedorDoXml != null) {
                    Optional<ProdutoFornecedor> vinculo = produtoFornecedorRepository
                            .findByFornecedorAndCodigoNoFornecedor(fornecedorDoXml, codXml);
                    if (vinculo.isPresent()) {
                        produtoEncontrado = vinculo.get().getProduto();
                        status = StatusMatch.MATCH_EXATO;
                        motivo = "Vínculo Fornecedor";
                    }
                }

                if (produtoEncontrado == null && isValidEAN(eanXml)) {
                    Optional<Produto> pEan = produtoRepository.findByCodigoBarras(eanXml);
                    if (pEan.isPresent()) {
                        produtoEncontrado = pEan.get();
                        status = StatusMatch.MATCH_EXATO;
                        motivo = "EAN";
                    }
                }

                if (produtoEncontrado != null) {
                    item.setIdProduto(produtoEncontrado.getId());
                    item.setNomeProdutoSugerido(produtoEncontrado.getDescricao());
                    item.setNovoProduto(false);
                } else {
                    item.setNovoProduto(true);
                }

                item.setStatusMatch(status);
                item.setMotivoMatch(motivo);
                retorno.getItensXml().add(item);
            }
            return retorno;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao ler XML: " + e.getMessage());
        }
    }

    // --- ATUALIZAÇÃO PRINCIPAL (LOTE) ---
    @Transactional
    public void processarEntradaEmLote(EntradaEstoqueDTO dto) {
        Fornecedor fornecedor = fornecedorRepository.findById(dto.getFornecedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado"));

        for (br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemEntradaDTO itemDto : dto.getItens()) {
            Produto produto;

            if (itemDto.getProdutoId() != null) {
                // Produto Existente
                produto = produtoRepository.findById(itemDto.getProdutoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto ID " + itemDto.getProdutoId() + " não encontrado."));
            } else {
                Optional<Produto> existente = produtoRepository.findByCodigoBarras(itemDto.getCodigoBarras());
                if (existente.isPresent()) {
                    produto = existente.get();
                } else {
                    // --- CRIAÇÃO DE PRODUTO COM CAMPOS DO FRONTEND ---
                    produto = new Produto();
                    produto.setDescricao(itemDto.getDescricao() != null ? itemDto.getDescricao().toUpperCase() : "PRODUTO NOVO");
                    produto.setCodigoBarras(itemDto.getCodigoBarras());
                    produto.setNcm(itemDto.getNcm());
                    produto.setUnidade(itemDto.getUnidade());

                    // Marca, Categoria e Subcategoria (vindos da edição no Grid)
                    produto.setMarca(itemDto.getMarca() != null && !itemDto.getMarca().isBlank() ? itemDto.getMarca().toUpperCase() : "GENERICA");
                    produto.setCategoria(itemDto.getCategoria() != null && !itemDto.getCategoria().isBlank() ? itemDto.getCategoria().toUpperCase() : "GERAL");
                    produto.setSubcategoria(itemDto.getSubcategoria() != null && !itemDto.getSubcategoria().isBlank() ? itemDto.getSubcategoria().toUpperCase() : produto.getCategoria());

                    // Origem: Permite seleção (0 ou 2) ou padroniza 0
                    produto.setOrigem(itemDto.getOrigem() != null && !itemDto.getOrigem().isBlank() ? itemDto.getOrigem() : "0");

                    // CST: Permite seleção (ex: 500 para ST) ou padroniza 102
                    produto.setCst(itemDto.getCst() != null && !itemDto.getCst().isBlank() ? itemDto.getCst() : "102");

                    // Preços
                    produto.setPrecoCusto(itemDto.getValorUnitario());
                    produto.setPrecoMedioPonderado(itemDto.getValorUnitario());
                    produto.setPrecoVenda(itemDto.getValorUnitario().multiply(new BigDecimal("1.5"))); // Margem 50% inicial

                    // Defaults
                    produto.setAtivo(true);
                    produto.setQuantidadeEmEstoque(0);
                    produto.setEstoqueFiscal(0);
                    produto.setEstoqueNaoFiscal(0);

                    produto = produtoRepository.save(produto);
                    salvarVinculoSeNaoExistir(fornecedor.getId(), produto, itemDto.getCodigoBarras());
                }
            }

            // Atualização de Saldo
            BigDecimal qtd = itemDto.getQuantidade();
            BigDecimal custo = itemDto.getValorUnitario();
            String doc = dto.getNumeroDocumento();

            BigDecimal novoPreco = calcularNovoPrecoMedio(produto, qtd, custo);
            produto.setPrecoCusto(novoPreco);
            produto.setPrecoMedioPonderado(novoPreco);

            if (doc != null && !doc.isBlank() && !doc.equals("S/N")) {
                produto.setEstoqueFiscal(produto.getEstoqueFiscal() + qtd.intValue());
            } else {
                produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() + qtd.intValue());
            }

            produto.atualizarSaldoTotal();
            produtoRepository.save(produto);

            registrarMovimentacao(produto, qtd, custo, TipoMovimentoEstoque.ENTRADA,
                    MotivoMovimentacaoDeEstoque.COMPRA_COM_NOTA_FISCAL, "Entrada via Importação", doc, fornecedor);
        }

        if (dto.getFormaPagamento() != null) {
            // CORRIGIDO: Removida a vírgula extra que havia aqui
            gerarFinanceiroBatch(dto, fornecedor);
        }
    }

    private Produto criarProdutoAutomatico(EstoqueRequestDTO dados) {
        Produto novo = new Produto();
        novo.setCodigoBarras(dados.getCodigoBarras());
        novo.setDescricao(dados.getDescricao() != null ? dados.getDescricao().toUpperCase() : "PRODUTO NOVO");
        novo.setNcm(dados.getNcm() != null ? dados.getNcm() : "00000000");
        novo.setUnidade(dados.getUnidade() != null ? dados.getUnidade() : "UN");

        // Dados Padrão para Entrada Unitária Manual
        novo.setMarca("GENERICA");
        novo.setCategoria("GERAL");
        novo.setOrigem("0");
        novo.setCst("102");

        novo.setPrecoCusto(dados.getPrecoCusto());
        novo.setPrecoMedioPonderado(dados.getPrecoCusto());
        novo.setPrecoVenda(dados.getPrecoCusto().multiply(new BigDecimal("1.5")));
        novo.setAtivo(true);
        novo.setQuantidadeEmEstoque(0);
        novo.setEstoqueFiscal(0);
        novo.setEstoqueNaoFiscal(0);

        return produtoRepository.save(novo);
    }

    // --- MÉTODOS AUXILIARES ---

    private void salvarVinculoSeNaoExistir(Long fornecedorId, Produto produto, String codigoFornecedor) {
        if (fornecedorId == null) return;
        Fornecedor fornecedor = fornecedorRepository.findById(fornecedorId).orElse(null);
        if (fornecedor != null) {
            boolean existe = produtoFornecedorRepository.existsByFornecedorAndCodigoNoFornecedor(fornecedor, codigoFornecedor);
            if (!existe) {
                ProdutoFornecedor vinculo = new ProdutoFornecedor();
                vinculo.setFornecedor(fornecedor);
                vinculo.setProduto(produto);
                vinculo.setCodigoNoFornecedor(codigoFornecedor);
                produtoFornecedorRepository.save(vinculo);
            }
        }
    }

    private BigDecimal calcularNovoPrecoMedio(Produto produto, BigDecimal qtdEntrada, BigDecimal custoNovo) {
        BigDecimal estoqueAtual = new BigDecimal(produto.getQuantidadeEmEstoque());
        BigDecimal custoMedioAtual = produto.getPrecoMedioPonderado() != null ? produto.getPrecoMedioPonderado() : BigDecimal.ZERO;
        if (produto.getQuantidadeEmEstoque() <= 0) return custoNovo;
        BigDecimal valorTotal = estoqueAtual.multiply(custoMedioAtual).add(qtdEntrada.multiply(custoNovo));
        BigDecimal novoEstoque = estoqueAtual.add(qtdEntrada);
        return novoEstoque.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : valorTotal.divide(novoEstoque, 4, RoundingMode.HALF_UP);
    }

    private void registrarMovimentacao(Produto p, BigDecimal qtd, BigDecimal custo, TipoMovimentoEstoque tipo, MotivoMovimentacaoDeEstoque motivo, String obs, String ref, Fornecedor f) {
        MovimentoEstoque mov = new MovimentoEstoque();
        mov.setProduto(p);
        mov.setDataMovimento(LocalDateTime.now());
        mov.setTipoMovimentoEstoque(tipo);
        mov.setQuantidadeMovimentada(qtd);
        mov.setCustoMovimentado(custo);
        mov.setMotivoMovimentacaoDeEstoque(motivo);
        mov.setObservacao(obs);
        mov.setDocumentoReferencia(ref);
        mov.setFornecedor(f);
        mov.setSaldoAtual(p.getQuantidadeEmEstoque());
        movimentoRepository.save(mov);
    }

    private String getElementValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) return list.item(0).getTextContent();
        return null;
    }

    private void atualizarDadosFornecedorPeloXml(Element emit, Fornecedor fornecedor) {
        try {
            String xNome = getElementValue(emit, "xNome");
            String xFant = getElementValue(emit, "xFant");
            String ie = getElementValue(emit, "IE");

            if (fornecedor.getRazaoSocial() == null || fornecedor.getRazaoSocial().isEmpty() || fornecedor.getRazaoSocial().matches(".*\\d{5,}.*")) {
                if (xNome != null) fornecedor.setRazaoSocial(xNome.toUpperCase());
            }
            if (fornecedor.getNomeFantasia() == null || fornecedor.getNomeFantasia().isEmpty()) {
                fornecedor.setNomeFantasia(xFant != null ? xFant.toUpperCase() : (xNome != null ? xNome.toUpperCase() : ""));
            }
            if (fornecedor.getInscricaoEstadual() == null || fornecedor.getInscricaoEstadual().isEmpty()) {
                fornecedor.setInscricaoEstadual(ie);
            }
            fornecedorRepository.save(fornecedor);
        } catch (Exception e) {
            System.err.println("Erro ao atualizar dados fornecedor: " + e.getMessage());
        }
    }

    private boolean isValidEAN(String ean) {
        return ean != null && !ean.equals("SEM GTIN") && ean.length() > 5;
    }

    // --- MÉTODOS FINANCEIROS (COM SOBRECARGA PARA ATENDER OS DOIS CASOS) ---

    // 1. Para LOTE (EntradaEstoqueDTO) - Usado em processarEntradaEmLote
    private void gerarFinanceiroBatch(EntradaEstoqueDTO dto, Fornecedor fornecedor) {
        BigDecimal valorTotalNota = dto.getItens().stream()
                .map(item -> item.getValorUnitario().multiply(item.getQuantidade()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (valorTotalNota.compareTo(BigDecimal.ZERO) <= 0) return;

        int parcelas = (dto.getQuantidadeParcelas() != null && dto.getQuantidadeParcelas() > 0) ? dto.getQuantidadeParcelas() : 1;
        BigDecimal valorParcela = valorTotalNota.divide(new BigDecimal(parcelas), 2, RoundingMode.HALF_UP);
        LocalDate dataBase = dto.getDataVencimento() != null ? dto.getDataVencimento() : LocalDate.now();

        for (int i = 0; i < parcelas; i++) {
            criarContaPagar(fornecedor, valorParcela, dto.getNumeroDocumento(), i + 1, parcelas, dataBase, dto.getFormaPagamento(), i);
        }
    }

    // 2. Para UNITÁRIO (EstoqueRequestDTO) - Substitui o antigo gerarFinanceiroEntrada
    private void gerarFinanceiroBatch(EstoqueRequestDTO dados, BigDecimal custoUnitario, Integer qtd) {
        if (dados.getFormaPagamento() == null) return;

        BigDecimal valorTotal = custoUnitario.multiply(new BigDecimal(qtd));
        Fornecedor fornecedor = null;

        if (dados.getFornecedorId() != null) {
            fornecedor = fornecedorRepository.findById(dados.getFornecedorId()).orElse(null);
        } else if (dados.getFornecedorCnpj() != null) {
            fornecedor = fornecedorRepository.findByCnpj(dados.getFornecedorCnpj()).orElse(null);
        }

        if (fornecedor == null) return;

        int parcelas = (dados.getQuantidadeParcelas() != null && dados.getQuantidadeParcelas() > 0) ? dados.getQuantidadeParcelas() : 1;
        BigDecimal valorParcela = valorTotal.divide(new BigDecimal(parcelas), 2, RoundingMode.HALF_UP);

        LocalDate dataBase = dados.getDataVencimentoBoleto() != null ? dados.getDataVencimentoBoleto() : LocalDate.now();

        for (int i = 0; i < parcelas; i++) {
            criarContaPagar(fornecedor, valorParcela, dados.getNumeroNotaFiscal(), i + 1, parcelas, dataBase, dados.getFormaPagamento(), i);
        }
    }

    // Método auxiliar para evitar repetição na criação da conta
    private void criarContaPagar(Fornecedor fornecedor, BigDecimal valor, String doc, int parcelaNum, int totalParcelas, LocalDate dataBase, FormaDePagamento formaPagto, int incrementoMes) {
        ContaPagar conta = new ContaPagar();
        conta.setFornecedor(fornecedor);
        conta.setValorTotal(valor);
        conta.setCategoria("COMPRA MERCADORIA");
        conta.setDescricao("Compra NF: " + (doc != null ? doc : "S/N") + " - Parc " + parcelaNum + "/" + totalParcelas);
        conta.setDataEmissao(LocalDate.now());

        if (formaPagto == FormaDePagamento.DINHEIRO ||
                formaPagto == FormaDePagamento.PIX ||
                formaPagto == FormaDePagamento.DEBITO) {
            conta.setDataVencimento(LocalDate.now());
            conta.setDataPagamento(LocalDate.now());
            conta.setStatus(StatusConta.PAGO);
        } else {
            conta.setDataVencimento(dataBase.plusMonths(incrementoMes));
            conta.setStatus(StatusConta.PENDENTE);
        }
        contaPagarRepository.save(conta);
    }
    // --- SAÍDA DE VENDA (Chamado pelo VendaService) ---
    @Transactional
    public void registrarSaidaVenda(Produto produto, Integer quantidade) {
        // Lógica: Tenta baixar do Estoque Fiscal primeiro (ou ajuste conforme sua preferência fiscal)
        if (produto.getEstoqueFiscal() >= quantidade) {
            produto.setEstoqueFiscal(produto.getEstoqueFiscal() - quantidade);
        } else {
            // Se não tem fiscal suficiente, zera o fiscal e tira o resto do não fiscal
            int restante = quantidade - produto.getEstoqueFiscal();
            produto.setEstoqueFiscal(0);
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() - restante);
        }

        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        registrarMovimentacao(produto, new BigDecimal(quantidade),
                produto.getPrecoMedioPonderado(),
                TipoMovimentoEstoque.SAIDA,
                MotivoMovimentacaoDeEstoque.VENDA,
                "Venda PDV", null, null);
    }

    // --- AJUSTE MANUAL (Chamado pelo Controller para Inventário) ---
    @Transactional
    public void realizarAjusteManual(AjusteEstoqueDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));

        int novaQuantidade = dados.getQuantidade().intValue();
        int diferenca = novaQuantidade - produto.getQuantidadeEmEstoque();

        // Se a diferença for positiva, é entrada de ajuste. Se negativa, é saída.
        if (diferenca > 0) {
            // Entrada por ajuste (Joga no Não Fiscal por padrão ou crie lógica)
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() + diferenca);
        } else {
            // Saída por ajuste
            int qtdBaixa = Math.abs(diferenca);
            if (produto.getEstoqueNaoFiscal() >= qtdBaixa) {
                produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() - qtdBaixa);
            } else {
                int resta = qtdBaixa - produto.getEstoqueNaoFiscal();
                produto.setEstoqueNaoFiscal(0);
                produto.setEstoqueFiscal(produto.getEstoqueFiscal() - resta);
            }
        }

        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);

        registrarMovimentacao(produto, new BigDecimal(Math.abs(diferenca)),
                produto.getPrecoMedioPonderado(),
                diferenca > 0 ? TipoMovimentoEstoque.ENTRADA : TipoMovimentoEstoque.SAIDA,
                diferenca > 0 ? MotivoMovimentacaoDeEstoque.AJUSTE_INVENTARIO_ENTRADA : MotivoMovimentacaoDeEstoque.AJUSTE_INVENTARIO_SAIDA,
                dados.getObservacao(), null, null);
    }
}