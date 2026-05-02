package com.bdmage.mage_backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.SceneForbiddenException;
import com.bdmage.mage_backend.exception.SceneNotFoundException;
import com.bdmage.mage_backend.exception.SceneOwnershipRequiredException;
import com.bdmage.mage_backend.exception.SceneTagAlreadyExistsException;
import com.bdmage.mage_backend.model.Scene;
import com.bdmage.mage_backend.model.SceneTag;
import com.bdmage.mage_backend.model.SceneTagId;
import com.bdmage.mage_backend.repository.SceneRepository;
import com.bdmage.mage_backend.repository.SceneTagRepository;
import com.bdmage.mage_backend.repository.TagRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SceneServiceTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createSceneTrimsFieldsAndPersistsForAuthenticatedUser() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.saveAndFlush(any(Scene.class))).thenAnswer(invocation -> invocation.getArgument(0, Scene.class));

		Scene savedScene = sceneService.createScene(
				42L,
				" Aurora Drift ",
				" A glassy nebula drift. ",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null);

		ArgumentCaptor<Scene> sceneCaptor = ArgumentCaptor.forClass(Scene.class);
		verify(sceneRepository).saveAndFlush(sceneCaptor.capture());

		Scene persistedScene = sceneCaptor.getValue();
		assertThat(persistedScene.getOwnerUserId()).isEqualTo(42L);
		assertThat(persistedScene.getName()).isEqualTo("Aurora Drift");
		assertThat(persistedScene.getDescription()).isEqualTo("A glassy nebula drift.");
		assertThat(persistedScene.getSceneData()).isEqualTo(this.objectMapper.readTree("""
				{"visualizer":{"shader":"nebula"}}
				"""));
		assertThat(persistedScene.getThumbnailRef()).isNull();

		assertThat(savedScene.getOwnerUserId()).isEqualTo(42L);
		assertThat(savedScene.getName()).isEqualTo("Aurora Drift");
		assertThat(savedScene.getDescription()).isEqualTo("A glassy nebula drift.");
		assertThat(savedScene.getThumbnailRef()).isNull();
	}

	@Test
	void createSceneStoresNullDescriptionWhenDescriptionIsBlank() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.saveAndFlush(any(Scene.class))).thenAnswer(invocation -> invocation.getArgument(0, Scene.class));

		Scene savedScene = sceneService.createScene(
				42L,
				"Aurora Drift",
				"   ",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null);

		ArgumentCaptor<Scene> sceneCaptor = ArgumentCaptor.forClass(Scene.class);
		verify(sceneRepository).saveAndFlush(sceneCaptor.capture());

		assertThat(sceneCaptor.getValue().getDescription()).isNull();
		assertThat(savedScene.getDescription()).isNull();
	}

	@Test
	void createSceneRejectsMissingAuthenticatedUserIdentity() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);

		assertThatThrownBy(() -> sceneService.createScene(
				null,
				"Scene Name",
				null,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verifyNoInteractions(userRepository);
		verifyNoInteractions(sceneRepository);
	}

	@Test
	void createSceneFinalizesThumbnailBeforePersistingScene() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(thumbnailStorageService.finalizeSceneCreationUpload(42L, "scenes/pending/42/thumbnails/abc123.png"))
				.thenReturn(new ThumbnailStorageService.FinalizedThumbnail(
						"scenes/pending/42/thumbnails/abc123.png",
						"https://cdn.example.com/scenes/pending/42/thumbnails/abc123.png"));
		when(sceneRepository.saveAndFlush(any(Scene.class))).thenAnswer(invocation -> invocation.getArgument(0, Scene.class));

		Scene savedScene = sceneService.createScene(
				42L,
				"Aurora Drift",
				null,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"scenes/pending/42/thumbnails/abc123.png");

		ArgumentCaptor<Scene> sceneCaptor = ArgumentCaptor.forClass(Scene.class);
		verify(sceneRepository).saveAndFlush(sceneCaptor.capture());
		verify(thumbnailStorageService).finalizeSceneCreationUpload(42L, "scenes/pending/42/thumbnails/abc123.png");

		assertThat(sceneCaptor.getValue().getThumbnailRef())
				.isEqualTo("https://cdn.example.com/scenes/pending/42/thumbnails/abc123.png");
		assertThat(sceneCaptor.getValue().getDescription()).isNull();
		assertThat(savedScene.getThumbnailRef())
				.isEqualTo("https://cdn.example.com/scenes/pending/42/thumbnails/abc123.png");
	}

	@Test
	void createSceneDeletesUploadedThumbnailWhenPersistenceFails() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(thumbnailStorageService.finalizeSceneCreationUpload(42L, "scenes/pending/42/thumbnails/abc123.png"))
				.thenReturn(new ThumbnailStorageService.FinalizedThumbnail(
						"scenes/pending/42/thumbnails/abc123.png",
						"https://cdn.example.com/scenes/pending/42/thumbnails/abc123.png"));
		when(sceneRepository.saveAndFlush(any(Scene.class))).thenThrow(new RuntimeException("db down"));

		assertThatThrownBy(() -> sceneService.createScene(
				42L,
				"Aurora Drift",
				null,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"scenes/pending/42/thumbnails/abc123.png"))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("db down");

		verify(thumbnailStorageService).delete("scenes/pending/42/thumbnails/abc123.png");
	}

	@Test
	void createSceneRejectsUnknownAuthenticatedUserIdentity() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);

		when(userRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> sceneService.createScene(
				99L,
				"Scene Name",
				null,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verify(userRepository).existsById(99L);
		verify(sceneRepository, never()).saveAndFlush(any(Scene.class));
	}

	@Test
	void deleteSceneDeletesOwnedSceneForAuthenticatedOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = new Scene(
				42L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		sceneService.deleteScene(42L, 15L);

		verify(userRepository).existsById(42L);
		verify(sceneRepository).findById(15L);
		verify(sceneRepository).delete(scene);
	}

	@Test
	void deleteSceneRejectsMissingScene() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> sceneService.deleteScene(42L, 15L))
				.isInstanceOf(SceneNotFoundException.class)
				.hasMessage("Scene not found.");

		verify(userRepository).existsById(42L);
		verify(sceneRepository).findById(15L);
		verify(sceneRepository, never()).delete(any(Scene.class));
	}

	@Test
	void deleteSceneRejectsAuthenticatedNonOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = new Scene(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThatThrownBy(() -> sceneService.deleteScene(42L, 15L))
				.isInstanceOf(SceneForbiddenException.class)
				.hasMessage("You do not have permission to delete this scene.");

		verify(sceneRepository, never()).delete(any(Scene.class));
	}

	@Test
	void getSceneReturnsSceneWhenItExists() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = new Scene(
				42L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"https://cdn.example.com/scenes/15/thumbnails/thumb.png");

		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThat(sceneService.getScene(15L)).isSameAs(scene);
		verify(sceneRepository).findById(15L);
		verifyNoInteractions(userRepository);
	}

	@Test
	void getSceneThrowsSceneNotFoundWhenItDoesNotExist() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);

		when(sceneRepository.findById(15L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> sceneService.getScene(15L))
				.isInstanceOf(SceneNotFoundException.class)
				.hasMessage("Scene not found.");
	}

	@Test
	void getScenesForUserReturnsScenesOwnedByRequestedUser() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene firstScene = scene(11L, 77L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));
		Scene secondScene = scene(12L, 77L, "Signal Bloom", Instant.parse("2026-03-26T16:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findAllByOwnerUserId(77L)).thenReturn(List.of(firstScene, secondScene));

		List<Scene> scenes = sceneService.getScenesForUser(42L, 77L);

		assertThat(scenes).extracting(Scene::getId).containsExactly(11L, 12L);
	}

	@Test
	void getScenesByTagNormalizesTagNameBeforeLookup() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = scene(15L, 77L, "Glass Orbit", Instant.parse("2026-03-26T17:00:00Z"));

		when(sceneRepository.findAllByTagName("ambient")).thenReturn(List.of(scene));

		List<Scene> scenes = sceneService.getScenesByTag("  Ambient  ");

		assertThat(scenes).extracting(Scene::getId).containsExactly(15L);
		verifyNoInteractions(userRepository);
	}

	@Test
	void getTagNamesForSceneReturnsSortedAttachedTagNames() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		SceneTagRepository sceneTagRepository = mock(SceneTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, tagRepository, sceneTagRepository, userRepository);

		when(sceneTagRepository.findAllBySceneId(15L))
				.thenReturn(List.of(new SceneTag(15L, 7L), new SceneTag(15L, 6L)));
		when(tagRepository.findAllById(List.of(7L, 6L)))
				.thenReturn(List.of(new com.bdmage.mage_backend.model.Tag("showcase"), new com.bdmage.mage_backend.model.Tag("ambient")));

		assertThat(sceneService.getTagNamesForScene(15L)).containsExactly("ambient", "showcase");
		verifyNoInteractions(userRepository);
	}

	@Test
	void attachTagToSceneSavesAssociationForAuthenticatedUser() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		SceneTagRepository sceneTagRepository = mock(SceneTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, tagRepository, sceneTagRepository, userRepository);
		Scene scene = new Scene(
				42L,
				"Aurora Drift",
				this.objectMapper.createObjectNode());

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(tagRepository.existsById(7L)).thenReturn(true);
		when(sceneTagRepository.existsBySceneIdAndTagId(15L, 7L)).thenReturn(false);
		when(sceneTagRepository.saveAndFlush(any(SceneTag.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, SceneTag.class));

		SceneTag savedSceneTag = sceneService.attachTagToScene(42L, 15L, 7L);

		assertThat(savedSceneTag.getSceneId()).isEqualTo(15L);
		assertThat(savedSceneTag.getTagId()).isEqualTo(7L);
	}

	@Test
	void attachTagToSceneRejectsDuplicateAssociation() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		SceneTagRepository sceneTagRepository = mock(SceneTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, tagRepository, sceneTagRepository, userRepository);
		Scene scene = new Scene(
				42L,
				"Aurora Drift",
				this.objectMapper.createObjectNode());

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(tagRepository.existsById(7L)).thenReturn(true);
		when(sceneTagRepository.existsBySceneIdAndTagId(15L, 7L)).thenReturn(true);

		assertThatThrownBy(() -> sceneService.attachTagToScene(42L, 15L, 7L))
				.isInstanceOf(SceneTagAlreadyExistsException.class)
				.hasMessage("This tag is already attached to the scene.");
	}

	@Test
	void attachTagToSceneRejectsNonOwner() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		SceneTagRepository sceneTagRepository = mock(SceneTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, tagRepository, sceneTagRepository, userRepository);
		Scene scene = new Scene(
				77L,
				"Not Yours",
				this.objectMapper.createObjectNode());

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThatThrownBy(() -> sceneService.attachTagToScene(42L, 15L, 7L))
				.isInstanceOf(SceneOwnershipRequiredException.class)
				.hasMessage("Scene ownership is required.");

		verifyNoInteractions(tagRepository);
		verify(sceneTagRepository, never()).saveAndFlush(any(SceneTag.class));
	}

	@Test
	void replaceSceneTagsClearsExistingTagsAndPersistsUniqueRequestedTags() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		SceneTagRepository sceneTagRepository = mock(SceneTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, tagRepository, sceneTagRepository, userRepository);
		Scene scene = new Scene(
				42L,
				"Aurora Drift",
				this.objectMapper.createObjectNode());
		SceneTag existingSceneTag = new SceneTag(15L, 4L);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(tagRepository.existsById(7L)).thenReturn(true);
		when(tagRepository.existsById(8L)).thenReturn(true);
		when(sceneTagRepository.findAllBySceneId(15L)).thenReturn(List.of(existingSceneTag));
		when(sceneTagRepository.saveAllAndFlush(any()))
				.thenAnswer(invocation -> invocation.getArgument(0, List.class));

		List<SceneTag> savedSceneTags = sceneService.replaceSceneTags(42L, 15L, List.of(7L, 8L, 7L));

		assertThat(savedSceneTags)
				.extracting(SceneTag::getTagId)
				.containsExactly(7L, 8L);
		verify(sceneTagRepository).deleteAll(List.of(existingSceneTag));
		verify(sceneTagRepository).flush();
	}

	@Test
	void replaceSceneTagsAllowsClearingAllTags() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		SceneTagRepository sceneTagRepository = mock(SceneTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, tagRepository, sceneTagRepository, userRepository);
		Scene scene = new Scene(
				42L,
				"Aurora Drift",
				this.objectMapper.createObjectNode());
		SceneTag existingSceneTag = new SceneTag(15L, 4L);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(sceneTagRepository.findAllBySceneId(15L)).thenReturn(List.of(existingSceneTag));

		List<SceneTag> savedSceneTags = sceneService.replaceSceneTags(42L, 15L, List.of());

		assertThat(savedSceneTags).isEmpty();
		verifyNoInteractions(tagRepository);
		verify(sceneTagRepository).deleteAll(List.of(existingSceneTag));
		verify(sceneTagRepository).flush();
		verify(sceneTagRepository, never()).saveAllAndFlush(any());
	}

	@Test
	void removeTagFromSceneDeletesExistingAssociationForOwner() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		SceneTagRepository sceneTagRepository = mock(SceneTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, tagRepository, sceneTagRepository, userRepository);
		Scene scene = new Scene(
				42L,
				"Aurora Drift",
				this.objectMapper.createObjectNode());
		SceneTagId sceneTagId = new SceneTagId(15L, 7L);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(tagRepository.existsById(7L)).thenReturn(true);
		when(sceneTagRepository.existsById(sceneTagId)).thenReturn(true);

		sceneService.removeTagFromScene(42L, 15L, 7L);

		verify(sceneTagRepository).deleteById(sceneTagId);
		verify(sceneTagRepository).flush();
	}

	@Test
	void createSceneThumbnailUploadReturnsPresignedUploadForAuthenticatedUser() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);
		ThumbnailStorageService.PresignedThumbnailUpload presignedUpload = new ThumbnailStorageService.PresignedThumbnailUpload(
				"scenes/pending/42/thumbnails/abc123.png",
				"https://upload.example.com/scenes/pending/42/thumbnails/abc123.png",
				"PUT",
				Map.of("Content-Type", "image/png"),
				Instant.parse("2026-03-26T15:10:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(thumbnailStorageService.createSceneCreationUpload(42L, "thumb.png", "image/png"))
				.thenReturn(presignedUpload);

		ThumbnailStorageService.PresignedThumbnailUpload result = sceneService.createSceneThumbnailUpload(
				42L,
				"thumb.png",
				"image/png",
				3L);

		assertThat(result).isEqualTo(presignedUpload);
		verify(thumbnailStorageService).createSceneCreationUpload(42L, "thumb.png", "image/png");
	}

	@Test
	void createSceneThumbnailUploadRejectsUnsupportedContentType() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);

		when(userRepository.existsById(42L)).thenReturn(true);

		assertThatThrownBy(() -> sceneService.createSceneThumbnailUpload(42L, "thumb.pdf", "application/pdf", 4L))
				.isInstanceOf(InvalidThumbnailException.class)
				.hasMessage("Thumbnail must be a valid image (jpeg, png, webp, or gif).");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void createThumbnailUploadReturnsPresignedUploadForOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);
		Scene scene = scene(15L, 42L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));
		ThumbnailStorageService.PresignedThumbnailUpload presignedUpload = new ThumbnailStorageService.PresignedThumbnailUpload(
				"scenes/15/thumbnails/abc123.png",
				"https://upload.example.com/scenes/15/thumbnails/abc123.png",
				"PUT",
				Map.of("Content-Type", "image/png"),
				Instant.parse("2026-03-26T15:10:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(thumbnailStorageService.createPresignedUpload(15L, "thumb.png", "image/png"))
				.thenReturn(presignedUpload);

		ThumbnailStorageService.PresignedThumbnailUpload result = sceneService.createThumbnailUpload(
				42L,
				15L,
				"thumb.png",
				"image/png",
				3L);

		assertThat(result).isEqualTo(presignedUpload);
		verify(thumbnailStorageService).createPresignedUpload(15L, "thumb.png", "image/png");
	}

	@Test
	void createThumbnailUploadRejectsUnsupportedContentType() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);
		Scene scene = scene(15L, 42L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThatThrownBy(() -> sceneService.createThumbnailUpload(42L, 15L, "thumb.pdf", "application/pdf", 4L))
				.isInstanceOf(InvalidThumbnailException.class)
				.hasMessage("Thumbnail must be a valid image (jpeg, png, webp, or gif).");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void createThumbnailUploadRejectsOversizedUpload() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);
		Scene scene = scene(15L, 42L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThatThrownBy(() -> sceneService.createThumbnailUpload(42L, 15L, "thumb.png", "image/png", 6L * 1024 * 1024))
				.isInstanceOf(InvalidThumbnailException.class)
				.hasMessage("Thumbnail must not exceed 5 MB.");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void createThumbnailUploadRejectsNonOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);
		Scene scene = scene(15L, 77L, "Not Yours", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThatThrownBy(() -> sceneService.createThumbnailUpload(42L, 15L, "thumb.png", "image/png", 3L))
				.isInstanceOf(SceneOwnershipRequiredException.class)
				.hasMessage("Scene ownership is required.");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void finalizeThumbnailUploadStoresPublicUrlAndDeletesPreviousThumbnail() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);
		Scene existingScene = new Scene(
				42L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"https://cdn.example.com/scenes/15/thumbnails/old-thumb.png");
		ReflectionTestUtils.setField(existingScene, "id", 15L);
		ReflectionTestUtils.setField(existingScene, "createdAt", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(existingScene));
		when(thumbnailStorageService.finalizeUpload(eq(15L), eq("scenes/15/thumbnails/new-thumb.png")))
				.thenReturn(new ThumbnailStorageService.FinalizedThumbnail(
						"scenes/15/thumbnails/new-thumb.png",
						"https://cdn.example.com/scenes/15/thumbnails/new-thumb.png"));
		when(sceneRepository.saveAndFlush(any(Scene.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, Scene.class));

		Scene result = sceneService.finalizeThumbnailUpload(42L, 15L, "scenes/15/thumbnails/new-thumb.png");

		assertThat(result.getThumbnailRef()).isEqualTo("https://cdn.example.com/scenes/15/thumbnails/new-thumb.png");
		verify(thumbnailStorageService).delete("https://cdn.example.com/scenes/15/thumbnails/old-thumb.png");
	}

	@Test
	void finalizeThumbnailUploadRejectsMissingScene() {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> sceneService.finalizeThumbnailUpload(42L, 15L, "scenes/15/thumbnails/new-thumb.png"))
				.isInstanceOf(SceneNotFoundException.class)
				.hasMessage("Scene not found.");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void finalizeThumbnailUploadRejectsNonOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		SceneService sceneService = sceneServiceWithStorage(sceneRepository, userRepository, thumbnailStorageService);
		Scene scene = scene(15L, 77L, "Not Yours", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThatThrownBy(() -> sceneService.finalizeThumbnailUpload(42L, 15L, "scenes/15/thumbnails/new-thumb.png"))
				.isInstanceOf(SceneOwnershipRequiredException.class)
				.hasMessage("Scene ownership is required.");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void updateDescriptionTrimsAndPersistsForOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = scene(15L, 42L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(sceneRepository.saveAndFlush(scene)).thenReturn(scene);

		Scene result = sceneService.updateDescription(42L, 15L, " Updated from My Scenes. ");

		assertThat(result).isSameAs(scene);
		assertThat(scene.getDescription()).isEqualTo("Updated from My Scenes.");
		verify(sceneRepository).saveAndFlush(scene);
	}

	@Test
	void updateDescriptionClearsBlankDescriptionForOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = new Scene(
				42L,
				"Aurora Drift",
				"Existing description.",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(sceneRepository.saveAndFlush(scene)).thenReturn(scene);

		Scene result = sceneService.updateDescription(42L, 15L, "   ");

		assertThat(result).isSameAs(scene);
		assertThat(scene.getDescription()).isNull();
		verify(sceneRepository).saveAndFlush(scene);
	}

	@Test
	void updateDescriptionRejectsNonOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = scene(15L, 77L, "Not Yours", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThatThrownBy(() -> sceneService.updateDescription(42L, 15L, "Updated from My Scenes."))
				.isInstanceOf(SceneOwnershipRequiredException.class)
				.hasMessage("Scene ownership is required.");

		verify(sceneRepository, never()).saveAndFlush(any(Scene.class));
	}

	@Test
	void updateSceneTrimsMetadataAndPersistsSceneDataForOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = scene(15L, 42L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));
		when(sceneRepository.saveAndFlush(scene)).thenReturn(scene);

		Scene result = sceneService.updateScene(
				42L,
				15L,
				" Updated Scene ",
				" Updated description. ",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"pulse"},"state":{"energy":0.5}}
						"""));

		assertThat(result).isSameAs(scene);
		assertThat(scene.getName()).isEqualTo("Updated Scene");
		assertThat(scene.getDescription()).isEqualTo("Updated description.");
		assertThat(scene.getSceneData()).isEqualTo(this.objectMapper.readTree("""
				{"visualizer":{"shader":"pulse"},"state":{"energy":0.5}}
				"""));
		verify(sceneRepository).saveAndFlush(scene);
	}

	@Test
	void updateSceneRejectsNonOwner() throws Exception {
		SceneRepository sceneRepository = mock(SceneRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		SceneService sceneService = sceneService(sceneRepository, userRepository);
		Scene scene = scene(15L, 77L, "Not Yours", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(sceneRepository.findById(15L)).thenReturn(Optional.of(scene));

		assertThatThrownBy(() -> sceneService.updateScene(
				42L,
				15L,
				"Updated Scene",
				"Updated description.",
				this.objectMapper.createObjectNode()))
				.isInstanceOf(SceneOwnershipRequiredException.class)
				.hasMessage("Scene ownership is required.");

		verify(sceneRepository, never()).saveAndFlush(any(Scene.class));
	}

	private SceneService sceneService(SceneRepository sceneRepository, UserRepository userRepository) {
		return sceneService(
				sceneRepository,
				mock(TagRepository.class),
				mock(SceneTagRepository.class),
				userRepository);
	}

	private SceneService sceneService(
			SceneRepository sceneRepository,
			TagRepository tagRepository,
			SceneTagRepository sceneTagRepository,
			UserRepository userRepository) {
		return new SceneService(sceneRepository, tagRepository, sceneTagRepository, userRepository);
	}

	private SceneService sceneServiceWithStorage(
			SceneRepository sceneRepository,
			UserRepository userRepository,
			ThumbnailStorageService thumbnailStorageService) {
		return new SceneService(
				sceneRepository,
				mock(TagRepository.class),
				mock(SceneTagRepository.class),
				userRepository,
				thumbnailStorageService,
				thumbnailStorageProperties());
	}

	private ThumbnailStorageProperties thumbnailStorageProperties() {
		return new ThumbnailStorageProperties(
				"aws-s3",
				"us-east-1",
				"mage-test-thumbnails",
				"scenes",
				null,
				null,
				null,
				null,
				null,
				"https://cdn.example.com",
				"image/jpeg,image/png,image/webp,image/gif",
				5L * 1024 * 1024,
				Duration.ofMinutes(10));
	}

	private Scene scene(Long sceneId, Long ownerUserId, String name, Instant createdAt) throws Exception {
		Scene scene = new Scene(
				ownerUserId,
				name,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		ReflectionTestUtils.setField(scene, "id", sceneId);
		ReflectionTestUtils.setField(scene, "createdAt", createdAt);
		return scene;
	}
}
