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
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest
@Transactional
// Configuração H2 Blindada (Igual ao teste que funcionou)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ddcosmeticos_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
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
        p.setAtivo(true);
        produtoRepository.save(p);

        // 2. Monta o Pedido vindo de SÃO PAULO
        PedidoCompraDTO dto = new PedidoCompraDTO();
        dto.setFornecedorNome("Distribuidora Paulista");
        dto.setUfOrigem("SP"); // Origem 7%
        dto.setUfDestino("PE"); // Destino 20.5% (Interna)

        ItemCompraDTO item = new ItemCompraDTO();
        item.setCodigoBarras("789_PERFUME_SP");
        item.setQuantidade(new BigDecimal("10")); // 10 unidades
        item.setPrecoUnitario(new BigDecimal("100.00")); // R$ 100 cada
        item.setMva(new BigDecimal("60.00")); // MVA 60% (Alta!)

        dto.setItens(List.of(item));

        // 3. Executa Simulação
        PedidoCompra pedidoSalvo = pedidoService.criarSimulacao(dto);

        // 4. Validações Matemáticas (O momento da verdade)

        // Valor Produtos: 10 * 100 = 1000
        Assertions.assertEquals(0, new BigDecimal("1000.00").compareTo(pedidoSalvo.getTotalProdutos()), "Total produtos incorreto");

        // Cálculo do Imposto Esperado (Unitário):
        // Base ST = 100 * 1.60 = 160
        // Débito PE = 160 * 0.205 = 32.80
        // Crédito SP = 100 * 0.07 = 7.00
        // ST a Pagar = 32.80 - 7.00 = 25.80 por unidade
        // Total ST (10 un) = 258.00

        BigDecimal impostoEsperado = new BigDecimal("258.00");
        BigDecimal impostoCalculado = pedidoSalvo.getTotalImpostosEstimados();

        // Margem de erro de 0.01 centavo devido a arredondamento
        Assertions.assertTrue(impostoCalculado.subtract(impostoEsperado).abs().doubleValue() < 0.1,
                "Erro no cálculo do imposto. Esperado: 258.00, Veio: " + impostoCalculado);

        // O Custo Final deve incluir o imposto
        Assertions.assertTrue(pedidoSalvo.getTotalFinal().compareTo(new BigDecimal("1258.00")) >= 0,
                "O total final deve somar produtos + impostos");

        System.out.println(">>> TESTE SP->PE SUCESSO: Imposto calculado R$ " + impostoCalculado);
    }

    @Test
    @DisplayName("Cenário PE -> PE: Imposto deve ser ZERO (Compra Local)")
    public void testeSimulacaoCompraLocal() {
        // 1. Cria Produto
        Produto p = new Produto();
        p.setCodigoBarras("789_SHAMPOO_LOCAL");
        p.setDescricao("SHAMPOO RECIFE");
        p.setAtivo(true);
        produtoRepository.save(p);

        // 2. Monta Pedido PE -> PE
        PedidoCompraDTO dto = new PedidoCompraDTO();
        dto.setFornecedorNome("Atacado Recife");
        dto.setUfOrigem("PE");
        dto.setUfDestino("PE");

        ItemCompraDTO item = new ItemCompraDTO();
        item.setCodigoBarras("789_SHAMPOO_LOCAL");
        item.setQuantidade(new BigDecimal("10"));
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setMva(new BigDecimal("50.00")); // MVA existe, mas não deve ser usada se for interno

        dto.setItens(List.of(item));

        // 3. Executa
        PedidoCompra pedidoSalvo = pedidoService.criarSimulacao(dto);

        // 4. Validações
        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(pedidoSalvo.getTotalImpostosEstimados()),
                "Compra interna (PE->PE) não deve ter ST de entrada calculada pelo sistema");

        Assertions.assertEquals(0, pedidoSalvo.getTotalProdutos().compareTo(pedidoSalvo.getTotalFinal()),
                "Na compra interna, Total Final deve ser igual ao Total Produtos");

        System.out.println(">>> TESTE PE->PE SUCESSO: Imposto ZERO confirmado.");
    }
}