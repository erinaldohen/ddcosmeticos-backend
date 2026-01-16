package br.com.lojaddcosmeticos.ddcosmeticos_backend.service;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.enums.TipoTributacaoReforma;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ImportacaoService {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    @Lazy
    private CalculadoraFiscalService calculadoraFiscalService;

    // --- PONTO DE ENTRADA WEB ---
    @Transactional
    public void importarProdutos(MultipartFile arquivo) {
        try {
            processarImportacao(arquivo.getInputStream(), arquivo.getOriginalFilename());
        } catch (IOException e) {
            log.error("Erro de IO", e);
            throw new RuntimeException("Erro ao ler arquivo.");
        }
    }

    // --- PONTO DE ENTRADA INTERNO ---
    @Transactional
    public void importarViaInputStream(InputStream inputStream) {
        processarImportacao(inputStream, "carga-inicial.csv");
    }

    // --- LÓGICA DE PROCESSAMENTO ---
    private void processarImportacao(InputStream inputStream, String nomeArquivo) {
        log.info("Iniciando importação REFINADA: {}", nomeArquivo);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String linhaCabecalho = reader.readLine();
            if (linhaCabecalho == null) throw new RuntimeException("Arquivo vazio.");

            // Remove BOM se existir
            if (linhaCabecalho.startsWith("\uFEFF")) linhaCabecalho = linhaCabecalho.substring(1);

            // Detecta separador
            String separador = linhaCabecalho.contains(";") ? ";" : ",";
            // Regex para ignorar separador dentro de aspas
            String regexSplit = separador + "(?=([^\"]*\"[^\"]*\")*[^\"]*$)";

            String[] headers = linhaCabecalho.split(regexSplit);

            // 1. MAPEAMENTO DE COLUNAS
            Map<String, Integer> mapa = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String h = limparString(headers[i]);
                mapa.put(h, i);
            }

            // Log para debug de colunas
            log.info("Colunas detectadas: {}", mapa.keySet());

            int salvos = 0;
            int pulados = 0;
            int linhaNum = 1;
            String linha;

            while ((linha = reader.readLine()) != null) {
                linhaNum++;
                // Split robusto: Se o regex falhar (retornar 1 coluna), tenta split simples
                String[] dados = linha.split(regexSplit, -1);
                if (dados.length < 2) {
                    dados = linha.split(separador, -1);
                }

                try {
                    // 1. EAN / CÓDIGO DE BARRAS
                    String eanRaw = getValor(dados, mapa, "CODIGO DE BARRAS", "BARRAS", "EAN", "GTIN");
                    String ean = tratarEanCientifico(eanRaw);

                    // Validação mínima de EAN
                    if (ean.length() < 7 || ean.equals("0")) {
                        pulados++;
                        continue; // Pula linha sem identificação válida
                    }

                    // Busca ou Cria
                    Produto produto = produtoRepository.findByCodigoBarras(ean).orElse(new Produto());
                    if (produto.getId() == null) {
                        produto.setCodigoBarras(ean);
                        produto.setAtivo(true);
                    }

                    // --- 2. DADOS BÁSICOS ---
                    String desc = getValor(dados, mapa, "DESCRICAO", "NOME", "PRODUTO");
                    if (!desc.isBlank()) produto.setDescricao(desc);

                    String cat = getValor(dados, mapa, "CATEGORIA");
                    if(!cat.isBlank()) produto.setCategoria(cat);

                    String sub = getValor(dados, mapa, "SUBCATEGORIA", "SUB");
                    if(!sub.isBlank()) produto.setSubcategoria(sub);

                    String marca = getValor(dados, mapa, "MARCA");
                    if(!marca.isBlank()) produto.setMarca(marca);

                    String unidade = getValor(dados, mapa, "UNIDADE", "UN");
                    if (!unidade.isBlank()) produto.setUnidade(unidade);

                    // --- 3. PREÇOS (Com arredondamento 2 casas) ---
                    BigDecimal prVenda = parseDinheiro(getValor(dados, mapa, "PRECO VENDA VAREJO", "PRECO VENDA", "VAREJO"));
                    BigDecimal prCusto = parseDinheiro(getValor(dados, mapa, "PRECO DE CUSTO", "CUSTO"));

                    if (prVenda != null) produto.setPrecoVenda(prVenda);
                    if (prCusto != null) produto.setPrecoCusto(prCusto);

                    // Defaults para não quebrar banco (Constraints Not Null)
                    if (produto.getPrecoVenda() == null) produto.setPrecoVenda(BigDecimal.ZERO);
                    if (produto.getPrecoCusto() == null) produto.setPrecoCusto(BigDecimal.ZERO);

                    // Preço Médio: Se null, copia custo. Sempre arredonda para 2 casas.
                    if (produto.getPrecoMedioPonderado() == null || produto.getPrecoMedioPonderado().compareTo(BigDecimal.ZERO) == 0) {
                        produto.setPrecoMedioPonderado(produto.getPrecoCusto());
                    }
                    if (produto.getPrecoMedioPonderado() != null) {
                        produto.setPrecoMedioPonderado(produto.getPrecoMedioPonderado().setScale(2, RoundingMode.HALF_UP));
                    }

                    // --- 4. ESTOQUE ---
                    // Estoque Mínimo (Busca específica para não confundir com Quantidade)
                    String estMinStr = getValor(dados, mapa, "ESTOQUE MINIMO", "MINIMO");
                    if(!estMinStr.isBlank()) {
                        try {
                            int min = new BigDecimal(estMinStr.replace(",", ".")).intValue();
                            produto.setEstoqueMinimo(min);
                        } catch (Exception e) {}
                    }
                    if (produto.getEstoqueMinimo() == null) produto.setEstoqueMinimo(0);

                    // Quantidade Atual
                    String estoqueStr = getValor(dados, mapa, "QUANTIDADE EM ESTOQUE", "QTD", "SALDO");
                    // Fallback: Se não achou pelo nome completo, tenta só "ESTOQUE" mas verifica se não pegou a coluna de Mínimo
                    if (estoqueStr.isBlank()) {
                        String temp = getValor(dados, mapa, "ESTOQUE");
                        // Só usa se for diferente do valor achado para minimo (evita duplicação de coluna)
                        if (!temp.equals(estMinStr)) estoqueStr = temp;
                    }

                    int qtdEstoque = 0;
                    if(!estoqueStr.isBlank()) {
                        try {
                            qtdEstoque = new BigDecimal(estoqueStr.replace(",", ".")).intValue();
                        } catch (Exception e) {}
                    }
                    produto.setQuantidadeEmEstoque(qtdEstoque);
                    produto.setEstoqueNaoFiscal(qtdEstoque);
                    if (produto.getEstoqueFiscal() == null) produto.setEstoqueFiscal(0);

                    // --- 5. DADOS FISCAIS ---
                    String ncm = getValor(dados, mapa, "NCM").replaceAll("\\D", "");
                    if (!ncm.isBlank()) produto.setNcm(ncm);

                    String cest = getValor(dados, mapa, "CEST").replaceAll("\\D", "");
                    if (!cest.isBlank()) produto.setCest(cest);

                    String origem = getValor(dados, mapa, "ORIGEM").replaceAll("\\D", "");
                    produto.setOrigem((origem.isEmpty()) ? "0" : origem.substring(0, 1));

                    String cfop = getValor(dados, mapa, "CFOP").replaceAll("\\D", "");

                    // Lógica CST Simplificada
                    if (produto.getCst() == null) {
                        if (cfop.startsWith("5405")) produto.setCst("60");
                        else produto.setCst("00");
                    }

                    // Calculadora Fiscal (aplica regras de negócio)
                    if (calculadoraFiscalService != null) {
                        calculadoraFiscalService.aplicarRegrasFiscais(produto);
                    } else {
                        // Fallback se o serviço não estiver disponível
                        if (produto.getClassificacaoReforma() == null) produto.setClassificacaoReforma(TipoTributacaoReforma.PADRAO);
                        produto.setImpostoSeletivo(false);
                        produto.setMonofasico(false);
                    }

                    produtoRepository.save(produto);
                    salvos++;

                } catch (Exception e) {
                    pulados++;
                    log.warn("Erro ao processar linha {}: {}", linhaNum, e.getMessage());
                }
            }
            log.info("Importação Finalizada. Salvos: {}, Pulados: {}", salvos, pulados);

        } catch (Exception e) {
            log.error("Erro fatal na importação", e);
            throw new RuntimeException("Erro na importação: " + e.getMessage());
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private String getValor(String[] dados, Map<String, Integer> mapa, String... chavesPossiveis) {
        for (String chave : chavesPossiveis) {
            String key = limparString(chave);
            for (Map.Entry<String, Integer> entry : mapa.entrySet()) {
                // Busca exata ou "contém" (ex: "QUANTIDADE EM ESTOQUE" contém "ESTOQUE")
                // A verificação de length > 4 evita falsos positivos com palavras curtas como "UN" em "MUNICIPIO"
                if (entry.getKey().equals(key) || (key.length() > 3 && entry.getKey().contains(key))) {
                    int index = entry.getValue();
                    if (index < dados.length) {
                        return dados[index].replace("\"", "").trim();
                    }
                }
            }
        }
        return "";
    }

    private String tratarEanCientifico(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            // Se parecer notação científica (E+) ou decimal (123.0)
            if (raw.toUpperCase().contains("E+") || raw.contains(".")) {
                BigDecimal bd = new BigDecimal(raw.replace(",", "."));
                return bd.toPlainString().replaceAll("\\D", "");
            }
        } catch (Exception e) {}
        return raw.replaceAll("\\D", "");
    }

    private BigDecimal parseDinheiro(String valorRaw) {
        if (valorRaw == null || valorRaw.isBlank()) return null;
        try {
            String limpo = valorRaw.replace("R$", "").replace(" ", "").trim();

            // Lógica para detecção de formato Brasileiro (1.000,00) vs Americano (1,000.00)
            // Se tiver vírgula no final (ex: ,00), é BR.
            if (limpo.contains(",") && (!limpo.contains(".") || limpo.indexOf(",") > limpo.lastIndexOf("."))) {
                limpo = limpo.replace(".", ""); // Remove milhar
                limpo = limpo.replace(",", "."); // Transforma decimal
            } else if (limpo.contains(",")) {
                // Caso contrário (ex: 1,000.00), remove a virgula de milhar
                limpo = limpo.replace(",", "");
            }

            BigDecimal val = new BigDecimal(limpo);
            return val.setScale(2, RoundingMode.HALF_UP); // Força 2 casas
        } catch (Exception e) {
            return null;
        }
    }

    private String limparString(String s) {
        if (s == null) return "";
        String nfd = Normalizer.normalize(s, Normalizer.Form.NFD);
        String semAcento = Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(nfd).replaceAll("");
        // Remove espaços, aspas e joga para uppercase para facilitar o 'match'
        return semAcento.toUpperCase().trim().replace("\"", "").replace(" ", "").replace("_", "");
    }
}