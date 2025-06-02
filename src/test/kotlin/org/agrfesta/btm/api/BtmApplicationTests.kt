package org.agrfesta.btm.api

import org.agrfesta.btm.api.controllers.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container

class BtmApplicationTests: AbstractIntegrationTest() {

	companion object {
		@Container
		@ServiceConnection
		val postgres = createPostgresContainer()
	}

	@Test fun contextLoads() {/**/}

}
