package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.model.Tag;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@Testcontainers
class PresetTagRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private PresetTagRepository presetTagRepository;

	@Autowired
	private PresetRepository presetRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private UserRepository userRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savePersistsPresetTagPairAndSupportsPresetAndTagLookups() throws Exception {
		User owner = this.userRepository.saveAndFlush(
				new User("preset-tag-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "Preset Tag Owner"));
		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Tag firstTag = this.tagRepository.saveAndFlush(new Tag("Ambient"));
		Tag secondTag = this.tagRepository.saveAndFlush(new Tag("Cinematic"));

		PresetTag firstPresetTag = this.presetTagRepository.saveAndFlush(new PresetTag(preset.getId(), firstTag.getId()));
		this.presetTagRepository.saveAndFlush(new PresetTag(preset.getId(), secondTag.getId()));

		this.entityManager.clear();

		List<PresetTag> presetTags = this.presetTagRepository.findAllByPresetId(preset.getId());
		List<PresetTag> ambientTagLinks = this.presetTagRepository.findAllByTagId(firstTag.getId());

		assertThat(firstPresetTag.getPresetId()).isEqualTo(preset.getId());
		assertThat(firstPresetTag.getTagId()).isEqualTo(firstTag.getId());
		assertThat(this.presetTagRepository.existsByPresetIdAndTagId(preset.getId(), firstTag.getId())).isTrue();
		assertThat(presetTags)
				.extracting(PresetTag::getTagId)
				.containsExactlyInAnyOrder(firstTag.getId(), secondTag.getId());
		assertThat(ambientTagLinks)
				.extracting(PresetTag::getPresetId)
				.containsExactly(preset.getId());
	}

	@Test
	void rejectsDuplicatePresetTagPairs() throws Exception {
		User owner = this.userRepository.saveAndFlush(
				new User("preset-tag-duplicate-owner-" + System.nanoTime() + "@example.com", "hashed-password-value",
						"Preset Tag Duplicate Owner"));
		Preset preset = this.presetRepository.saveAndFlush(new Preset(
				owner.getId(),
				"Glass Orbit",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"glass"}}
						""")));
		Tag tag = this.tagRepository.saveAndFlush(new Tag("showcase"));

		this.presetTagRepository.saveAndFlush(new PresetTag(preset.getId(), tag.getId()));
		this.entityManager.clear();

		assertThatThrownBy(() -> {
			this.entityManager.persist(new PresetTag(preset.getId(), tag.getId()));
			this.entityManager.flush();
		}).isInstanceOf(PersistenceException.class);
	}
}
