package com.bdmage.mage_backend.controller;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.PresetRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.bdmage.mage_backend.service.PasswordHashingService;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PresetControllerIntegrationTests extends PostgresIntegrationTestSupport {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PresetRepository presetRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordHashingService passwordHashingService;

	@Test
	void createPresetReturnsUnauthorizedWhenRequestHasNoAuthenticationHeader() throws Exception {
		this.mockMvc.perform(post("/presets")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"Aurora Drift",
						  "sceneData":{"visualizer":{"shader":"nebula"}}
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.message").value("Authentication is required."));
	}

	@Test
	void createPresetPersistsPresetForTokenAuthenticatedUser() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "preset-user-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		User savedUser = this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Preset User"));

		MvcResult loginResult = this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		MvcResult createResult = this.mockMvc.perform(post("/presets")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" Aurora Drift ",
						  "sceneData":{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}},
						  "thumbnailRef":"  thumbnails/preset-1.png  "
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ownerUserId").value(savedUser.getId()))
				.andExpect(jsonPath("$.name").value("Aurora Drift"))
				.andExpect(jsonPath("$.sceneData.visualizer.shader").value("nebula"))
				.andExpect(jsonPath("$.thumbnailRef").value("thumbnails/preset-1.png"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andReturn();

		Long presetId = presetId(createResult);
		Preset savedPreset = this.presetRepository.findById(presetId).orElseThrow();

		assertThat(savedPreset.getOwnerUserId()).isEqualTo(savedUser.getId());
		assertThat(savedPreset.getName()).isEqualTo("Aurora Drift");
		assertThat(savedPreset.getSceneData().path("visualizer").path("shader").asText()).isEqualTo("nebula");
		assertThat(savedPreset.getThumbnailRef()).isEqualTo("thumbnails/preset-1.png");
		assertThat(savedPreset.getCreatedAt()).isNotNull();
	}

	@Test
	void createPresetRejectsInvalidRequestBody() throws Exception {
		String uniqueSuffix = String.valueOf(System.nanoTime());
		String email = "preset-validation-" + uniqueSuffix + "@example.com";
		String password = "password-" + uniqueSuffix;

		this.userRepository.saveAndFlush(new User(
				email,
				this.passwordHashingService.hash(password),
				"Preset Validation User"));

		MvcResult loginResult = this.mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequestBody(email, password)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		String accessToken = accessToken(loginResult);

		this.mockMvc.perform(post("/presets")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":" ",
						  "thumbnailRef":"thumb"
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.details.name").value("name must not be blank"))
				.andExpect(jsonPath("$.details.sceneData").value("sceneData must not be null"));
	}

	private static String loginRequestBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private static String accessToken(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private static Long presetId(MvcResult result) throws Exception {
		Matcher matcher = Pattern.compile("\"presetId\":(\\d+)")
				.matcher(result.getResponse().getContentAsString());
		assertThat(matcher.find()).isTrue();
		return Long.valueOf(matcher.group(1));
	}
}
