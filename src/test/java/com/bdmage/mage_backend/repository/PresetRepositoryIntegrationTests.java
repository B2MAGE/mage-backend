package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class PresetRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private PresetRepository presetRepository;

	@Autowired
	private UserRepository userRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savePersistsPresetWithJsonSceneData() throws Exception {
		User owner = userRepository.saveAndFlush(
				new User("preset-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "Preset Owner"));
		Preset savedPreset = presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"thumbnails/preset-1.png"));

		this.entityManager.clear();

		Preset foundPreset = presetRepository.findById(savedPreset.getId()).orElseThrow();

		assertThat(savedPreset.getId()).isNotNull();
		assertThat(foundPreset.getOwnerUserId()).isEqualTo(owner.getId());
		assertThat(foundPreset.getName()).isEqualTo("Aurora Drift");
		assertThat(foundPreset.getSceneData()).isEqualTo(this.objectMapper.readTree("""
				{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
				"""));
		assertThat(foundPreset.getThumbnailRef()).isEqualTo("thumbnails/preset-1.png");
		assertThat(foundPreset.getCreatedAt()).isNotNull();
	}

	@Test
	void findAllByOwnerUserIdReturnsOnlyMatchingOwnerPresets() throws Exception {
		User firstOwner = userRepository.saveAndFlush(
				new User("first-preset-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "First Owner"));
		User secondOwner = userRepository.saveAndFlush(
				new User("second-preset-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "Second Owner"));

		Preset firstOwnerPreset = presetRepository.saveAndFlush(new Preset(
				firstOwner.getId(),
				"First Owner Preset",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"ember"}}
						""")));
		Preset secondOwnerPreset = presetRepository.saveAndFlush(new Preset(
				secondOwner.getId(),
				"Second Owner Preset",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"glacier"}}
						""")));

		this.entityManager.clear();

		List<Preset> firstOwnerPresets = presetRepository.findAllByOwnerUserId(firstOwner.getId());

		assertThat(firstOwnerPresets).hasSize(1);
		assertThat(firstOwnerPresets)
				.extracting(Preset::getId)
				.containsExactly(firstOwnerPreset.getId());
		assertThat(firstOwnerPresets)
				.extracting(Preset::getName)
				.containsExactly("First Owner Preset");
		assertThat(firstOwnerPresets)
				.extracting(Preset::getId)
				.doesNotContain(secondOwnerPreset.getId());
	}
}
