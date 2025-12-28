package br.com.lojaddcosmeticos.ddcosmeticos_backend.integration;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.ItemCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.dto.PedidoCompraDTO;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.PedidoCompra;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.model.Produto;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.PedidoCompraRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.repository.ProdutoRepository;
import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.PedidoCompraService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@Transactional
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class PedidoCompraIntegrationTest {

    @Autowired private PedidoCompraService pedidoService;
    @Autowired private ProdutoRepository produtoRepository;
    @Autowired private PedidoCompraRepository pedidoRepository;

    @Test
    @DisplayName("Cenário SP -> PE: Deve calcular ST e aumentar o custo final")
    public void testeSimulacaoCompraInterestadual() {
        // 1. Cria Produto Base
        Produto p = new Produto();
        p.setCodigoBarras("789_PERFUME_SP");
        p.setDescricao("PERFUME IMPORTADO 100ML");
        // CORREÇÃO: Estoque Integer
        p.setQuantidadeEmEstoque(0);
        p.setAtivo(true);
        produtoRepository.save(p);

        // 2. Monta o Pedido vindo de SÃO PAULO
        PedidoCompraDTO dto = new PedidoCompraDTO();
        dto.setFornecedorNome("Distribuidora Paulista");
        dto.setUfOrigem("SP");
        dto.setUfDestino("PE");

        ItemCompraDTO item = new ItemCompraDTO();
        item.setCodigoBarras("789_PERFUME_SP");
        item.setQuantidade(new BigDecimal("10"));
        item.setPrecoUnitario(new BigDecimal("100.00"));
        item.setMva(new BigDecimal("60.00"));
        dto.setItens(List.of(item));

        // 3. Executa Simulação
        PedidoCompra pedidoSalvo = pedidoService.criarSimulacao(dto);

        // 4. Validações
        Assertions.assertEquals(0, new BigDecimal("1000.00").compareTo(pedidoSalvo.getTotalProdutos()), "Total produtos incorreto");

        // Cálculo esperado:
        // Base ST = 100 * 1.60 = 160 -> Débito = 32.80 -> Crédito 7.00 -> ST = 25.80 unit
        // Total ST = 258.00
        BigDecimal impostoEsperado = new BigDecimal("258.00");
        BigDecimal impostoCalculado = pedidoSalvo.getTotalImpostosEstimados();

        Assertions.assertTrue(impostoCalculado.subtract(impostoEsperado).abs().doubleValue() < 0.1,
                "Erro no cálculo do imposto. Esperado: 258.00, Veio: " + impostoCalculado);

        Assertions.assertTrue(pedidoSalvo.getTotalFinal().compareTo(new BigDecimal("1258.00")) >= 0,
                "O total final deve somar produtos + impostos");
    }

    @Test
    @DisplayName("Cenário PE -> PE: Imposto deve ser ZERO (Compra Local)")
    public void testeSimulacaoCompraLocal() {
        Produto p = new Produto();
        p.setCodigoBarras("789_SHAMPOO_LOCAL");
        p.setDescricao("SHAMPOO RECIFE");
        // CORREÇÃO: Estoque Integer
        p.setQuantidadeEmEstoque(0);
        p.setAtivo(true);
        produtoRepository.save(p);

        PedidoCompraDTO dto = new PedidoCompraDTO();
        dto.setFornecedorNome("Atacado Recife");
        dto.setUfOrigem("PE");
        dto.setUfDestino("PE");

        ItemCompraDTO item = new ItemCompraDTO();
        item.setCodigoBarras("789_SHAMPOO_LOCAL");
        item.setQuantidade(new BigDecimal("10"));
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setMva(new BigDecimal("50.00"));

        dto.setItens(List.of(item));

        PedidoCompra pedidoSalvo = pedidoService.criarSimulacao(dto);

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(pedidoSalvo.getTotalImpostosEstimados()),
                "Compra interna não deve ter ST");
        Assertions.assertEquals(0, pedidoSalvo.getTotalProdutos().compareTo(pedidoSalvo.getTotalFinal()),
                "Total Final deve ser igual ao Total Produtos");
    }
}