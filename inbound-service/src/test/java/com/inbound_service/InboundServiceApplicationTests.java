package com.inbound_service;

import com.common.client.warehouse.WarehouseStockGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class InboundServiceApplicationTests {

	@MockBean
	private WarehouseStockGateway warehouseStockGateway;

	@Test
	void contextLoads() {
	}

}
