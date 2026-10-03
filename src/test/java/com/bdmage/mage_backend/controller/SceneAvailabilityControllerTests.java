package com.bdmage.mage_backend.controller;

import java.time.Instant;
import java.util.List;

import com.bdmage.mage_backend.config.AuthenticationInterceptor;
import com.bdmage.mage_backend.config.SceneAvailabilityCacheInterceptor;
import com.bdmage.mage_backend.dto.CustomRenderingAvailabilityResponse;
import com.bdmage.mage_backend.dto.SceneAvailabilityResponse;
import com.bdmage.mage_backend.dto.SceneControlResponse;
import com.bdmage.mage_backend.exception.ApiExceptionHandler;
import com.bdmage.mage_backend.exception.CustomRenderingReleaseRequiredException;
import com.bdmage.mage_backend.exception.InvalidSceneAvailabilityRequestException;
import com.bdmage.mage_backend.exception.OperatorAccessRequiredException;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.service.AuthenticationTokenService;
import com.bdmage.mage_backend.service.SceneAvailabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SceneAvailabilityControllerTests {

	private SceneAvailabilityService availability;
	private AuthenticationTokenService tokens;
	private MockMvc mvc;
	private LocalValidatorFactoryBean validator;

	@BeforeEach
	void setUp() {
		this.availability = mock(SceneAvailabilityService.class);
		this.tokens = mock(AuthenticationTokenService.class);
		User operator = new User("operator@example.com", "unused-test-hash", "Operator");
		ReflectionTestUtils.setField(operator, "id", 7L);
		when(this.tokens.authenticate("operator-token")).thenReturn(operator);
		this.validator = new LocalValidatorFactoryBean();
		this.validator.afterPropertiesSet();
		this.mvc = MockMvcBuilders.standaloneSetup(
				new SceneAvailabilityController(this.availability),
				new SceneAvailabilityAdminController(this.availability))
				.addInterceptors(new SceneAvailabilityCacheInterceptor(), new AuthenticationInterceptor(this.tokens))
				.setControllerAdvice(new ApiExceptionHandler())
				.setValidator(this.validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		this.validator.close();
	}

	@Test
	void publicBatchReturnsSafeStatusWithoutSourceOrAudit() throws Exception {
		when(this.availability.statusesByIds(List.of(15L, 16L))).thenReturn(List.of(
				new SceneAvailabilityResponse(15L, false, "SCENE_DISABLED", "This scene is unavailable."),
				new SceneAvailabilityResponse(16L, true, "AVAILABLE", "This scene is available.")));
		this.mvc.perform(get("/api/scene-availability?ids=15,16"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$[0].sceneId").value(15))
				.andExpect(jsonPath("$[0].available").value(false))
				.andExpect(jsonPath("$[1].available").value(true))
				.andExpect(jsonPath("$[0].sceneData").doesNotExist())
				.andExpect(jsonPath("$[0].reason").doesNotExist())
				.andExpect(jsonPath("$[0].changedByUserId").doesNotExist());
		verifyNoInteractions(this.tokens);
	}

	@Test
	void publicRenderingStatusIsNotCached() throws Exception {
		when(this.availability.customRenderingStatus()).thenReturn(
				new CustomRenderingAvailabilityResponse(false, "CUSTOM_RENDERING_DISABLED", "Custom rendering is unavailable."));
		this.mvc.perform(get("/api/rendering-status"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.reason").doesNotExist());
	}

	@Test
	void malformedIdsUseApiErrorContractWithoutReflectingTheirValues() throws Exception {
		this.mvc.perform(get("/api/scene-availability?ids=15,private-note"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.ids").value("Parameter has an invalid value."));
		verifyNoInteractions(this.availability);
	}

	@Test
	void missingIdsUsesApiValidationError() throws Exception {
		when(this.availability.statusesByIds(null)).thenThrow(new InvalidSceneAvailabilityRequestException(
				"Provide between 1 and 100 scene IDs."));
		this.mvc.perform(get("/api/scene-availability"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"/api/admin/scenes/15/availability", "/api/admin/rendering/custom", "/api/scenes/15/repair"})
	void unauthenticatedProtectedReadsNeverReachServices(String path) throws Exception {
		this.mvc.perform(get(path))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		verifyNoInteractions(this.availability);
	}

	@Test
	void mutationUsesAuthenticatedIdAndReturnsPrivateAuditOnlyOnAdminRoute() throws Exception {
		when(this.availability.setSceneControl(15L, true, "GPU failure", 7L)).thenReturn(
				new SceneControlResponse(15L, true, 7L, Instant.parse("2026-10-03T12:00:00Z"), "GPU failure"));
		this.mvc.perform(put("/api/admin/scenes/15/availability")
				.header("Authorization", "Bearer operator-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"disabled":true,"reason":"GPU failure","changedByUserId":999}
						"""))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.disabled").value(true))
				.andExpect(jsonPath("$.changedByUserId").value(7))
				.andExpect(jsonPath("$.reason").value("GPU failure"));
		verify(this.availability).setSceneControl(15L, true, "GPU failure", 7L);
	}

	@Test
	void mutationRequiresAnExplicitTargetStateAndReason() throws Exception {
		this.mvc.perform(put("/api/admin/scenes/15/availability")
				.header("Authorization", "Bearer operator-token")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.disabled").value("disabled is required"))
				.andExpect(jsonPath("$.details.reason").value("reason must not be blank"));
		verifyNoInteractions(this.availability);
	}

	@Test
	void customRenderingRequiresAnExplicitTargetState() throws Exception {
		this.mvc.perform(put("/api/admin/rendering/custom")
				.header("Authorization", "Bearer operator-token")
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Incident\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.enabled").value("enabled is required"));
		verifyNoInteractions(this.availability);
	}

	@Test
	void boundedReasonsAreValidatedBeforeCallingService() throws Exception {
		this.mvc.perform(put("/api/admin/rendering/custom")
				.header("Authorization", "Bearer operator-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":false,\"reason\":\"" + "a".repeat(1001) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.reason").value("reason must be at most 1000 characters"));
		verifyNoInteractions(this.availability);
	}

	@Test
	void mapsAuthenticatedOperatorDenialToForbidden() throws Exception {
		when(this.availability.customRenderingControl(7L)).thenThrow(
				new OperatorAccessRequiredException("Operator access is required."));
		this.mvc.perform(get("/api/admin/rendering/custom").header("Authorization", "Bearer operator-token"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("OPERATOR_ACCESS_REQUIRED"));
	}

	@Test
	void mapsIsolationReleaseGateToConflict() throws Exception {
		when(this.availability.setCustomRenderingControl(true, "Enable", 7L)).thenThrow(
				new CustomRenderingReleaseRequiredException("Custom rendering release approval is required."));
		this.mvc.perform(put("/api/admin/rendering/custom")
				.header("Authorization", "Bearer operator-token")
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Enable\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CUSTOM_RENDERING_RELEASE_REQUIRED"));
	}

	@Test
	void repairReturnsSourceMarkedNonPlayableWithoutOperatorNotes() throws Exception {
		Scene scene = new Scene(7L, "Repair me", new ObjectMapper().readTree("{\"shader\":\"original source\"}"));
		ReflectionTestUtils.setField(scene, "id", 15L);
		when(this.availability.repairScene(15L, 7L)).thenReturn(scene);
		when(this.availability.status(scene)).thenReturn(
				new SceneAvailabilityResponse(15L, false, "SCENE_DISABLED", "This scene is unavailable."));
		this.mvc.perform(get("/api/scenes/15/repair").header("Authorization", "Bearer operator-token"))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.sceneData.shader").value("original source"))
				.andExpect(jsonPath("$.playable").value(false))
				.andExpect(jsonPath("$.availability.available").value(false))
				.andExpect(jsonPath("$.reason").doesNotExist());
		verify(this.availability).repairScene(15L, 7L);
	}
}
