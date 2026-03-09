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

class HealthControllerTests {

	private ReadinessService readinessService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.readinessService = mock(ReadinessService.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(this.readinessService)).build();
	}

	@Test
	void healthReturnsUp() throws Exception {
		this.mockMvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void readyReturnsUpWhenServiceReportsReady() throws Exception {
		when(this.readinessService.checkReadiness()).thenReturn(new ReadinessService.ReadinessStatus(true, "UP"));

		this.mockMvc.perform(get("/ready"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.database").value("UP"));
	}

	@Test
	void readyReturnsServiceUnavailableWhenServiceReportsNotReady() throws Exception {
		when(this.readinessService.checkReadiness()).thenReturn(new ReadinessService.ReadinessStatus(false, "DOWN"));

		this.mockMvc.perform(get("/ready"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("DOWN"))
			.andExpect(jsonPath("$.database").value("DOWN"));
	}
}
