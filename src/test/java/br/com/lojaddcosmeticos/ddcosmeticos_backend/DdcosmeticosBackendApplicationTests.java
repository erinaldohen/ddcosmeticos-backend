package br.com.lojaddcosmeticos.ddcosmeticos_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase; // Importante

@SpringBootTest
@ActiveProfiles("test") // Força o uso do application.properties criado acima
@AutoConfigureTestDatabase // Garante a troca do banco real pelo H2
class DdcosmeticosBackendApplicationTests {

	@Test
	void contextLoads() {
		// Se este teste passar, significa que o Spring conseguiu subir sem erros
	}
}