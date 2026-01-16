package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.AjusteEstoqueDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.EstoqueRequestDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.RetornoImportacaoXmlDTO;
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

        gerarFinanceiroEntrada(dados, custoUnitario, qtdEntrada.intValue());
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

            // 1. FORNECEDOR
            Fornecedor fornecedorDoXml = null;
            NodeList nListEmit = doc.getElementsByTagName("emit");
            if (nListEmit.getLength() > 0) {
                Element emit = (Element) nListEmit.item(0);
                String cnpj = getElementValue(emit, "CNPJ");

                if (cnpj != null) {
                    fornecedorDoXml = fornecedorService.buscarOuCriarRapido(cnpj);

                    // --- ATUALIZA DADOS COMPLETOS DO FORNECEDOR ---
                    atualizarDadosFornecedorPeloXml(emit, fornecedorDoXml);
                    // ----------------------------------------------

                    retorno.setFornecedorId(fornecedorDoXml.getId());
                    retorno.setNomeFornecedor(fornecedorDoXml.getRazaoSocial());
                }
            }

            // 2. DADOS DA NOTA
            NodeList nListIde = doc.getElementsByTagName("ide");
            if (nListIde.getLength() > 0) {
                retorno.setNumeroNota(getElementValue((Element) nListIde.item(0), "nNF"));
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

                // INTELIGÊNCIA DE MATCHING
                Produto produtoEncontrado = null;
                StatusMatch status = StatusMatch.NOVO_PRODUTO;
                String motivo = "Novo Produto";

                // Camada 1: Vínculo
                if (fornecedorDoXml != null) {
                    Optional<ProdutoFornecedor> vinculo = produtoFornecedorRepository
                            .findByFornecedorAndCodigoNoFornecedor(fornecedorDoXml, codXml);
                    if (vinculo.isPresent()) {
                        produtoEncontrado = vinculo.get().getProduto();
                        status = StatusMatch.MATCH_EXATO;
                        motivo = "Vínculo Fornecedor Encontrado";
                    }
                }

                // Camada 2: EAN
                if (produtoEncontrado == null && isValidEAN(eanXml)) {
                    Optional<Produto> pEan = produtoRepository.findByCodigoBarras(eanXml);
                    if (pEan.isPresent()) {
                        produtoEncontrado = pEan.get();
                        status = StatusMatch.MATCH_EXATO;
                        motivo = "Código de Barras (EAN)";
                    }
                }

                // Camada 3: Similaridade
                if (produtoEncontrado == null && ncmXml != null) {
                    List<Produto> candidatos = produtoRepository.findByNcm(ncmXml);
                    Produto melhorCandidato = null;
                    double maiorScore = 0.0;

                    for (Produto p : candidatos) {
                        double score = calcularSimilaridade(descXml, p.getDescricao());
                        if (score > 0.80 && score > maiorScore) {
                            if (extrairNumeros(descXml).equals(extrairNumeros(p.getDescricao()))) {
                                maiorScore = score;
                                melhorCandidato = p;
                            }
                        }
                    }

                    if (melhorCandidato != null) {
                        produtoEncontrado = melhorCandidato;
                        status = StatusMatch.SUGESTAO_FORTE;
                        motivo = "Nome Similar (" + Math.round(maiorScore * 100) + "%)";
                    }
                }

                if (produtoEncontrado != null) {
                    item.setIdProduto(produtoEncontrado.getId());
                    item.setNomeProdutoSugerido(produtoEncontrado.getDescricao());
                    item.setNovoProduto(false);

                    BigDecimal precoAtual = produtoEncontrado.getPrecoCusto() != null ? produtoEncontrado.getPrecoCusto() : BigDecimal.ZERO;
                    if (precoAtual.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal variacao = item.getPrecoCusto().subtract(precoAtual).abs().divide(precoAtual, 2, RoundingMode.HALF_UP);
                        if (variacao.compareTo(new BigDecimal("0.50")) > 0) {
                            item.setAlertaDivergencia(true);
                            motivo += " (Alerta: Preço Varia > 50%)";
                        }
                    }
                } else {
                    item.setNovoProduto(true);
                }

                item.setStatusMatch(status);
                item.setMotivoMatch(motivo);
                retorno.getItensXml().add(item);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao ler XML: " + e.getMessage());
        }

        return retorno;
    }

    // --- MÉTODOS DE SUPORTE (INCLUINDO O QUE FALTAVA) ---

    // MÉTODO NOVO: Atualiza dados do fornecedor baseado nas tags do XML
    private void atualizarDadosFornecedorPeloXml(Element emit, Fornecedor fornecedor) {
        try {
            String xNome = getElementValue(emit, "xNome");
            String xFant = getElementValue(emit, "xFant");
            String ie = getElementValue(emit, "IE");

            if (fornecedor.getRazaoSocial() == null || fornecedor.getRazaoSocial().contains("Fornecedor") || fornecedor.getRazaoSocial().isEmpty()) {
                fornecedor.setRazaoSocial(xNome);
            }
            if (fornecedor.getNomeFantasia() == null || fornecedor.getNomeFantasia().isEmpty()) {
                fornecedor.setNomeFantasia(xFant != null ? xFant : xNome);
            }
            if (fornecedor.getInscricaoEstadual() == null || fornecedor.getInscricaoEstadual().isEmpty()) {
                fornecedor.setInscricaoEstadual(ie);
            }

            NodeList nListEnder = emit.getElementsByTagName("enderEmit");
            if (nListEnder.getLength() > 0) {
                Element ender = (Element) nListEnder.item(0);
                if (fornecedor.getCep() == null) fornecedor.setCep(getElementValue(ender, "CEP"));
                if (fornecedor.getLogradouro() == null) fornecedor.setLogradouro(getElementValue(ender, "xLgr"));
                if (fornecedor.getNumero() == null) fornecedor.setNumero(getElementValue(ender, "nro"));
                if (fornecedor.getBairro() == null) fornecedor.setBairro(getElementValue(ender, "xBairro"));
                if (fornecedor.getCidade() == null) fornecedor.setCidade(getElementValue(ender, "xMun"));
                if (fornecedor.getEstado() == null) fornecedor.setEstado(getElementValue(ender, "UF"));
            }

            fornecedorRepository.save(fornecedor);

        } catch (Exception e) {
            System.err.println("Erro ao atualizar dados do fornecedor pelo XML: " + e.getMessage());
        }
    }

    private void gerarFinanceiroEntrada(EstoqueRequestDTO dados, BigDecimal custoUnitario, Integer qtd) {
        if (dados.getFormaPagamento() == null) return;

        BigDecimal valorTotal = custoUnitario.multiply(new BigDecimal(qtd));

        Fornecedor fornecedor = null;
        if (dados.getFornecedorId() != null) {
            fornecedor = fornecedorRepository.findById(dados.getFornecedorId()).orElse(null);
        } else if (dados.getFornecedorCnpj() != null) {
            fornecedor = fornecedorRepository.findByCpfOuCnpj(dados.getFornecedorCnpj()).orElse(null);
        }

        int parcelas = dados.getQuantidadeParcelas() != null && dados.getQuantidadeParcelas() > 0 ? dados.getQuantidadeParcelas() : 1;
        BigDecimal valorParcela = valorTotal.divide(new BigDecimal(parcelas), 2, RoundingMode.HALF_UP);

        for (int i = 1; i <= parcelas; i++) {
            ContaPagar conta = new ContaPagar();
            conta.setFornecedor(fornecedor);
            conta.setValorTotal(valorParcela);
            conta.setCategoria("COMPRA MERCADORIA");
            conta.setDescricao("Compra ref. " + (dados.getNumeroNotaFiscal() != null ? dados.getNumeroNotaFiscal() : "Estoque") + " - Parc " + i + "/" + parcelas);
            conta.setDataEmissao(LocalDate.now());

            if (dados.getFormaPagamento() == FormaDePagamento.DINHEIRO ||
                    dados.getFormaPagamento() == FormaDePagamento.PIX ||
                    dados.getFormaPagamento() == FormaDePagamento.DEBITO) {
                conta.setDataVencimento(LocalDate.now());
                conta.setDataPagamento(LocalDate.now());
                conta.setStatus(StatusConta.PAGO);
            } else {
                LocalDate vencto = dados.getDataVencimentoBoleto() != null ?
                        dados.getDataVencimentoBoleto() :
                        LocalDate.now().plusDays(30L * i);
                conta.setDataVencimento(vencto);
                conta.setStatus(StatusConta.PENDENTE);
            }
            contaPagarRepository.save(conta);
        }
    }

    private void salvarVinculoSeNaoExistir(Long fornecedorId, Produto produto, String codigoFornecedor) {
        Fornecedor fornecedor = fornecedorRepository.findById(fornecedorId).orElse(null);
        if (fornecedor != null) {
            boolean existe = produtoFornecedorRepository
                    .existsByFornecedorAndCodigoNoFornecedor(fornecedor, codigoFornecedor);
            if (!existe) {
                ProdutoFornecedor vinculo = new ProdutoFornecedor();
                vinculo.setFornecedor(fornecedor);
                vinculo.setProduto(produto);
                vinculo.setCodigoNoFornecedor(codigoFornecedor);
                produtoFornecedorRepository.save(vinculo);
            }
        }
    }

    private Produto criarProdutoAutomatico(EstoqueRequestDTO dados) {
        Produto novo = new Produto();
        novo.setCodigoBarras(dados.getCodigoBarras());
        novo.setDescricao(dados.getDescricao() != null ? dados.getDescricao().toUpperCase() : "PRODUTO NOVO " + dados.getCodigoBarras());
        novo.setNcm(dados.getNcm() != null ? dados.getNcm() : "00000000");
        novo.setUnidade(dados.getUnidade() != null ? dados.getUnidade() : "UN");
        novo.setOrigem("0");
        novo.setCst("102");
        novo.setPrecoCusto(dados.getPrecoCusto());
        novo.setPrecoMedioPonderado(dados.getPrecoCusto());
        novo.setPrecoVenda(dados.getPrecoCusto().multiply(new BigDecimal("1.5")));
        novo.setAtivo(true);
        novo.setMarca("GENERICA");
        novo.setCategoria("GERAL");
        novo.setEstoqueMinimo(5);
        novo.setQuantidadeEmEstoque(0);
        novo.setEstoqueFiscal(0);
        novo.setEstoqueNaoFiscal(0);
        return produtoRepository.save(novo);
    }

    private double calcularSimilaridade(String s1, String s2) {
        String n1 = normalizarTexto(s1);
        String n2 = normalizarTexto(s2);

        String longer = n1, shorter = n2;
        if (n1.length() < n2.length()) { longer = n2; shorter = n1; }
        int longerLength = longer.length();
        if (longerLength == 0) return 1.0;

        int[] costs = new int[shorter.length() + 1];
        for (int i = 0; i <= shorter.length(); i++) costs[i] = i;
        for (int i = 1; i <= longer.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= shorter.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        longer.charAt(i - 1) == shorter.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return (longerLength - costs[shorter.length()]) / (double) longerLength;
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        return texto.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private String extrairNumeros(String s) {
        if (s == null) return "";
        return s.replaceAll("[^0-9]", "");
    }

    private boolean isValidEAN(String ean) {
        return ean != null && !ean.equals("SEM GTIN") && !ean.isEmpty() && ean.length() > 5;
    }

    private String getElementValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return null;
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

    @Transactional
    public void registrarSaidaVenda(Produto produto, Integer quantidade) {
        if (produto.getEstoqueFiscal() >= quantidade) {
            produto.setEstoqueFiscal(produto.getEstoqueFiscal() - quantidade);
        } else {
            int restante = quantidade - produto.getEstoqueFiscal();
            produto.setEstoqueFiscal(0);
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() - restante);
        }
        produto.atualizarSaldoTotal();
        produtoRepository.save(produto);
        registrarMovimentacao(produto, new BigDecimal(quantidade), produto.getPrecoMedioPonderado(), TipoMovimentoEstoque.SAIDA, MotivoMovimentacaoDeEstoque.VENDA, null, null, null);
    }

    @Transactional
    public void realizarAjusteManual(AjusteEstoqueDTO dados) {
        Produto produto = produtoRepository.findByCodigoBarras(dados.getCodigoBarras())
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        int novaQuantidade = dados.getQuantidade().intValue();
        int diferenca = novaQuantidade - produto.getQuantidadeEmEstoque();
        if (diferenca > 0) {
            produto.setEstoqueNaoFiscal(produto.getEstoqueNaoFiscal() + diferenca);
        } else {
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
        registrarMovimentacao(produto, new BigDecimal(Math.abs(diferenca)), produto.getPrecoMedioPonderado(),
                diferenca > 0 ? TipoMovimentoEstoque.ENTRADA : TipoMovimentoEstoque.SAIDA,
                diferenca > 0 ? MotivoMovimentacaoDeEstoque.AJUSTE_INVENTARIO_ENTRADA : MotivoMovimentacaoDeEstoque.AJUSTE_INVENTARIO_SAIDA,
                dados.getObservacao(), null, null);
    }
}