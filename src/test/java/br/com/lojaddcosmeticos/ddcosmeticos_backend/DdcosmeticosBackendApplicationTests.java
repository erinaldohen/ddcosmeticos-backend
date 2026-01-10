package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import br.com.lojaddcosmeticos.ddcosmeticos_backend.service.CalculadoraFiscalService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test") // Garanta que application-test.properties existe e usa H2
class DdcosmeticosBackendApplicationTests {

	// Mockamos o serviço que depende de tabelas que talvez não existam no H2 vazio
	@MockitoBean private CalculadoraFiscalService calculadoraFiscalService;

	@Test
	void contextLoads() {
		// Verifica se o Spring consegue iniciar
	}
}