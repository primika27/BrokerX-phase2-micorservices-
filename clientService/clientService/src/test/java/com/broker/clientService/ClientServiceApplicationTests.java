package com.broker.clientService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = ClientServiceApplication.class)
@ActiveProfiles("test")
class ClientServiceApplicationTests {

	@Test
	void contextLoads() {
		// Test that the application context loads successfully
	}

}
