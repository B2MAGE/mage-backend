package com.bdmage.mage_backend.repository;

import java.util.List;

import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.Tag;
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
class SceneRepositoryIntegrationTests extends PostgresIntegrationTestSupport {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private SceneRepository sceneRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private SceneTagRepository sceneTagRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savePersistsSceneWithJsonSceneData() throws Exception {
		User owner = userRepository.saveAndFlush(
				new User("scene-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "Scene Owner"));
		Scene savedScene = sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				"Soft teal bloom with low-end drift.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
						"""),
				"thumbnails/scene-1.png"));

		this.entityManager.clear();

		Scene foundScene = sceneRepository.findById(savedScene.getId()).orElseThrow();

		assertThat(savedScene.getId()).isNotNull();
		assertThat(foundScene.getOwnerUserId()).isEqualTo(owner.getId());
		assertThat(foundScene.getName()).isEqualTo("Aurora Drift");
		assertThat(foundScene.getDescription()).isEqualTo("Soft teal bloom with low-end drift.");
		assertThat(foundScene.getSceneData()).isEqualTo(this.objectMapper.readTree("""
				{"visualizer":{"shader":"nebula"},"state":{"energy":0.92}}
				"""));
		assertThat(foundScene.getThumbnailRef()).isEqualTo("thumbnails/scene-1.png");
		assertThat(foundScene.getCreatedAt()).isNotNull();
	}

	@Test
	void findAllByOwnerUserIdReturnsOnlyMatchingOwnerScenes() throws Exception {
		User firstOwner = userRepository.saveAndFlush(
				new User("first-scene-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "First Owner"));
		User secondOwner = userRepository.saveAndFlush(
				new User("second-scene-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "Second Owner"));

		Scene firstOwnerScene = sceneRepository.saveAndFlush(new Scene(
				firstOwner.getId(),
				"First Owner Scene",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"ember"}}
						""")));
		Scene secondOwnerScene = sceneRepository.saveAndFlush(new Scene(
				secondOwner.getId(),
				"Second Owner Scene",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"glacier"}}
						""")));

		this.entityManager.clear();

		List<Scene> firstOwnerScenes = sceneRepository.findAllByOwnerUserId(firstOwner.getId());

		assertThat(firstOwnerScenes).hasSize(1);
		assertThat(firstOwnerScenes)
				.extracting(Scene::getId)
				.containsExactly(firstOwnerScene.getId());
		assertThat(firstOwnerScenes)
				.extracting(Scene::getName)
				.containsExactly("First Owner Scene");
		assertThat(firstOwnerScenes)
				.extracting(Scene::getDescription)
				.containsExactly((String) null);
		assertThat(firstOwnerScenes)
				.extracting(Scene::getId)
				.doesNotContain(secondOwnerScene.getId());
	}

	@Test
	void findAllByTagNameReturnsOnlyScenesLinkedToThatTag() throws Exception {
		User owner = this.userRepository.saveAndFlush(
				new User("tagged-scene-owner-" + System.nanoTime() + "@example.com", "hashed-password-value", "Tagged Scene Owner"));
		Scene ambientScene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						""")));
		Scene showcaseScene = this.sceneRepository.saveAndFlush(new Scene(
				owner.getId(),
				"Signal Bloom",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"}}
						""")));
		Tag ambientTag = this.tagRepository.saveAndFlush(new Tag("Ambient"));
		Tag showcaseTag = this.tagRepository.saveAndFlush(new Tag("showcase"));
		this.sceneTagRepository.saveAndFlush(new SceneTag(ambientScene.getId(), ambientTag.getId()));
		this.sceneTagRepository.saveAndFlush(new SceneTag(showcaseScene.getId(), showcaseTag.getId()));

		this.entityManager.clear();

		List<Scene> ambientScenes = this.sceneRepository.findAllByTagName("ambient");

		assertThat(ambientScenes)
				.extracting(Scene::getId)
				.containsExactly(ambientScene.getId());
		assertThat(ambientScenes)
				.extracting(Scene::getName)
				.containsExactly("Aurora Drift");
	}
}
