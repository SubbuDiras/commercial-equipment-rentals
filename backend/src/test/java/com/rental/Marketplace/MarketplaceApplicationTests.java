package com.rental.Marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class)
@ActiveProfiles("test")
class MarketplaceApplicationTests {

	@Test
	void contextLoads() {
	}

}
