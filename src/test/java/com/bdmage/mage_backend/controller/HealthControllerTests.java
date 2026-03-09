package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.service.ReadinessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HealthControllerTests
 *
 * These are **unit tests** for the HealthController.
 * 
 * A *unit test* checks a small piece of code in isolation without starting
 * the full Spring Boot application. This makes tests fast and easy to run.
 *
 * These tests do NOT start the full Spring Boot application. Instead we create
 * the controller directly and test it in isolation.
 *
 * We use MockMvc to simulate HTTP requests without running a real web server.
 */
class HealthControllerTests {

	/**
	 * This is a mocked version of the ReadinessService.
	 *
	 * A mock is a fake object used in tests. It allows us to control what the
	 * service returns so we can test different scenarios.
	 */
	private ReadinessService readinessService;

	/**
	 * MockMvc simulates HTTP requests (GET, POST, etc.) and lets us inspect
	 * the response exactly as a real client would receive it.
	 */
	private MockMvc mockMvc;

	/**
	 * This method runs before every test.
	 *
	 * It creates:
	 * • a mock ReadinessService
	 * • a standalone controller instance
	 * • a MockMvc test client
	 *
	 * "standaloneSetup" means we are only testing this controller, not the
	 * entire Spring application context.
	 */
	@BeforeEach
	void setUp() {
		this.readinessService = mock(ReadinessService.class);

		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new HealthController(this.readinessService))
				.build();
	}

	/**
	 * Test: healthReturnsUp
	 *
	 * The /health endpoint is a "liveness check".
	 * Its job is simply to confirm that the application is running.
	 *
	 * Expected response:
	 *
	 * GET /health
	 *
	 * {
	 * "status": "UP"
	 * }
	 */
	@Test
	void healthReturnsUp() throws Exception {

		this.mockMvc.perform(get("/health"))

				// HTTP status should be 200 OK
				.andExpect(status().isOk())

				// Response should be JSON
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

				// JSON field "status" should equal "UP"
				.andExpect(jsonPath("$.status").value("UP"));
	}

	/**
	 * Test: readyReturnsUpWhenServiceReportsReady
	 *
	 * This test simulates the case where the ReadinessService reports that
	 * the application is ready to accept traffic.
	 *
	 * We use Mockito's "when(...)" to control what the mocked service returns.
	 */
	@Test
	void readyReturnsUpWhenServiceReportsReady() throws Exception {

		// Configure the mock service to say the app is ready
		when(this.readinessService.checkReadiness())
				.thenReturn(new ReadinessService.ReadinessStatus(true, "UP"));

		this.mockMvc.perform(get("/ready"))

				// Controller should return HTTP 200
				.andExpect(status().isOk())

				// Response should be JSON
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

				// Verify JSON response values
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"));
	}

	/**
	 * Test: readyReturnsServiceUnavailableWhenServiceReportsNotReady
	 *
	 * This test simulates the situation where the application is running
	 * but not ready to serve requests yet (for example, the database is down).
	 *
	 * In that case the controller should return HTTP 503.
	 *
	 * 503 = "Service Unavailable"
	 */
	@Test
	void readyReturnsServiceUnavailableWhenServiceReportsNotReady() throws Exception {

		// Configure the mock service to report "not ready"
		when(this.readinessService.checkReadiness())
				.thenReturn(new ReadinessService.ReadinessStatus(false, "DOWN"));

		this.mockMvc.perform(get("/ready"))

				// Controller should return HTTP 503
				.andExpect(status().isServiceUnavailable())

				// Response should be JSON
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

				// Verify JSON response values
				.andExpect(jsonPath("$.status").value("DOWN"))
				.andExpect(jsonPath("$.database").value("DOWN"));
	}
}