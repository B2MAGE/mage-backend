package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HealthControllerIntegrationTests
 *
 * This is a **high-level integration test** that starts the entire Spring Boot
 * application.
 *
 * Unlike unit tests (which test a single class), this test verifies that the
 * whole application can start correctly with its real configuration.
 * 
 * In this case that includes:
 * • the Spring Boot application
 * • the real HTTP controller
 * • the datasource configuration
 * • a temporary PostgreSQL database
 *
 * The goal is to confirm that all of these pieces work together correctly.
 */
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)

/*
 * @SpringBootTest starts the entire Spring Boot application context.
 * This means our controllers, services, and configuration classes are
 * created the same way they would be in the real application.
 */
@SpringBootTest

/*
 * @AutoConfigureMockMvc creates a MockMvc instance that allows us to
 * simulate HTTP requests (GET, POST, etc.) without starting a real server.
 *
 * Think of MockMvc as a lightweight HTTP client for testing controllers.
 */
@AutoConfigureMockMvc

/*
 * @Testcontainers tells JUnit that this test class uses Testcontainers.
 *
 * Testcontainers automatically starts a temporary Docker container for
 * services like PostgreSQL so tests can run against a real database.
 */
@Testcontainers
class HealthControllerIntegrationTests extends PostgresIntegrationTestSupport {

	/*
	 * MockMvc is injected by Spring and lets us simulate HTTP requests
	 * against our controllers.
	 */
	@Autowired
	private MockMvc mockMvc;

	/**
	 * Test: readyReturnsUpWhenDatabaseIsReachable
	 *
	 * This test verifies that the `/ready` endpoint reports that the
	 * application is ready when the database is available.
	 *
	 * Expected behavior:
	 *
	 * GET /ready
	 *
	 * Response:
	 * {
	 * "status": "UP",
	 * "database": "UP"
	 * }
	 */
	@Test
	void readyReturnsUpWhenDatabaseIsReachable() throws Exception {

		/*
		 * Perform a simulated HTTP GET request to /ready
		 */
		this.mockMvc.perform(get("/ready"))

				/*
				 * Expect HTTP status 200 OK
				 */
				.andExpect(status().isOk())

				/*
				 * Expect JSON field "status" to equal "UP"
				 */
				.andExpect(jsonPath("$.status").value("UP"))

				/*
				 * Expect JSON field "database" to equal "UP"
				 */
				.andExpect(jsonPath("$.database").value("UP"));
	}
}
