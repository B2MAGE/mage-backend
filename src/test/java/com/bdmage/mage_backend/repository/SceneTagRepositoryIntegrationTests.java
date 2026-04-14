package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
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
class SceneTagRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private SceneTagRepository sceneTagRepository;

	@Autowired
	private SceneRepository sceneRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private UserRepository userRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savePersistsSceneTagPairAndSupportsSceneAndTagLookups() throws Exception {
		User owner = this.userRepository.saveAndFlush(
				new User("scene-tag-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "Scene Tag Owner"));
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Tag firstTag = this.tagRepository.saveAndFlush(new Tag("Ambient"));
		Tag secondTag = this.tagRepository.saveAndFlush(new Tag("Cinematic"));

		SceneTag firstSceneTag = this.sceneTagRepository.saveAndFlush(new SceneTag(scene.getId(), firstTag.getId()));
		this.sceneTagRepository.saveAndFlush(new SceneTag(scene.getId(), secondTag.getId()));

		this.entityManager.clear();

		List<SceneTag> sceneTags = this.sceneTagRepository.findAllBySceneId(scene.getId());
		List<SceneTag> ambientTagLinks = this.sceneTagRepository.findAllByTagId(firstTag.getId());

		assertThat(firstSceneTag.getSceneId()).isEqualTo(scene.getId());
		assertThat(firstSceneTag.getTagId()).isEqualTo(firstTag.getId());
		assertThat(this.sceneTagRepository.existsBySceneIdAndTagId(scene.getId(), firstTag.getId())).isTrue();
		assertThat(sceneTags)
				.extracting(SceneTag::getTagId)
				.containsExactlyInAnyOrder(firstTag.getId(), secondTag.getId());
		assertThat(ambientTagLinks)
				.extracting(SceneTag::getSceneId)
				.containsExactly(scene.getId());
	}

	@Test
	void rejectsDuplicateSceneTagPairs() throws Exception {
		User owner = this.userRepository.saveAndFlush(
				new User("scene-tag-duplicate-owner-" + System.nanoTime() + "@example.com", "hashed-password-value",
						"Scene Tag Duplicate Owner"));
		Scene scene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Glass Orbit",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"glass"}}
						""")));
		Tag tag = this.tagRepository.saveAndFlush(new Tag("showcase"));

		this.sceneTagRepository.saveAndFlush(new SceneTag(scene.getId(), tag.getId()));
		this.entityManager.clear();

		assertThatThrownBy(() -> {
			this.entityManager.persist(new SceneTag(scene.getId(), tag.getId()));
			this.entityManager.flush();
		}).isInstanceOf(PersistenceException.class);
	}
}
