package com.bdmage.mage_backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bdmage.mage_backend.config.ThumbnailStorageProperties;
import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.InvalidThumbnailException;
import com.bdmage.mage_backend.exception.PresetForbiddenException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
import com.bdmage.mage_backend.exception.PresetOwnershipRequiredException;
import com.bdmage.mage_backend.exception.PresetTagAlreadyExistsException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.PresetTag;
import com.bdmage.mage_backend.repository.PresetRepository;
import com.bdmage.mage_backend.repository.PresetTagRepository;
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

class PresetServiceTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void createPresetTrimsFieldsAndPersistsForAuthenticatedUser() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.saveAndFlush(any(Preset.class))).thenAnswer(invocation -> invocation.getArgument(0, Preset.class));

		Preset savedPreset = presetService.createPreset(
				42L,
				" Aurora Drift ",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null);

		ArgumentCaptor<Preset> presetCaptor = ArgumentCaptor.forClass(Preset.class);
		verify(presetRepository).saveAndFlush(presetCaptor.capture());

		Preset persistedPreset = presetCaptor.getValue();
		assertThat(persistedPreset.getOwnerUserId()).isEqualTo(42L);
		assertThat(persistedPreset.getName()).isEqualTo("Aurora Drift");
		assertThat(persistedPreset.getSceneData()).isEqualTo(this.objectMapper.readTree("""
				{"visualizer":{"shader":"nebula"}}
				"""));
		assertThat(persistedPreset.getThumbnailRef()).isNull();

		assertThat(savedPreset.getOwnerUserId()).isEqualTo(42L);
		assertThat(savedPreset.getName()).isEqualTo("Aurora Drift");
		assertThat(savedPreset.getThumbnailRef()).isNull();
	}

	@Test
	void createPresetRejectsMissingAuthenticatedUserIdentity() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);

		assertThatThrownBy(() -> presetService.createPreset(
				null,
				"Preset Name",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verifyNoInteractions(userRepository);
		verifyNoInteractions(presetRepository);
	}

	@Test
	void createPresetFinalizesThumbnailBeforePersistingPreset() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(thumbnailStorageService.finalizePresetCreationUpload(42L, "presets/pending/42/thumbnails/abc123.png"))
				.thenReturn(new ThumbnailStorageService.FinalizedThumbnail(
						"presets/pending/42/thumbnails/abc123.png",
						"https://cdn.example.com/presets/pending/42/thumbnails/abc123.png"));
		when(presetRepository.saveAndFlush(any(Preset.class))).thenAnswer(invocation -> invocation.getArgument(0, Preset.class));

		Preset savedPreset = presetService.createPreset(
				42L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"presets/pending/42/thumbnails/abc123.png");

		ArgumentCaptor<Preset> presetCaptor = ArgumentCaptor.forClass(Preset.class);
		verify(presetRepository).saveAndFlush(presetCaptor.capture());
		verify(thumbnailStorageService).finalizePresetCreationUpload(42L, "presets/pending/42/thumbnails/abc123.png");

		assertThat(presetCaptor.getValue().getThumbnailRef())
				.isEqualTo("https://cdn.example.com/presets/pending/42/thumbnails/abc123.png");
		assertThat(savedPreset.getThumbnailRef())
				.isEqualTo("https://cdn.example.com/presets/pending/42/thumbnails/abc123.png");
	}

	@Test
	void createPresetDeletesUploadedThumbnailWhenPersistenceFails() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(thumbnailStorageService.finalizePresetCreationUpload(42L, "presets/pending/42/thumbnails/abc123.png"))
				.thenReturn(new ThumbnailStorageService.FinalizedThumbnail(
						"presets/pending/42/thumbnails/abc123.png",
						"https://cdn.example.com/presets/pending/42/thumbnails/abc123.png"));
		when(presetRepository.saveAndFlush(any(Preset.class))).thenThrow(new RuntimeException("db down"));

		assertThatThrownBy(() -> presetService.createPreset(
				42L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"presets/pending/42/thumbnails/abc123.png"))
				.isInstanceOf(RuntimeException.class)
				.hasMessage("db down");

		verify(thumbnailStorageService).delete("presets/pending/42/thumbnails/abc123.png");
	}

	@Test
	void createPresetRejectsUnknownAuthenticatedUserIdentity() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);

		when(userRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> presetService.createPreset(
				99L,
				"Preset Name",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				null))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verify(userRepository).existsById(99L);
		verify(presetRepository, never()).saveAndFlush(any(Preset.class));
	}

	@Test
	void deletePresetDeletesOwnedPresetForAuthenticatedOwner() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);
		Preset preset = new Preset(
				42L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));

		presetService.deletePreset(42L, 15L);

		verify(userRepository).existsById(42L);
		verify(presetRepository).findById(15L);
		verify(presetRepository).delete(preset);
	}

	@Test
	void deletePresetRejectsMissingPreset() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> presetService.deletePreset(42L, 15L))
				.isInstanceOf(PresetNotFoundException.class)
				.hasMessage("Preset not found.");

		verify(userRepository).existsById(42L);
		verify(presetRepository).findById(15L);
		verify(presetRepository, never()).delete(any(Preset.class));
	}

	@Test
	void deletePresetRejectsAuthenticatedNonOwner() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);
		Preset preset = new Preset(
				77L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));

		assertThatThrownBy(() -> presetService.deletePreset(42L, 15L))
				.isInstanceOf(PresetForbiddenException.class)
				.hasMessage("You do not have permission to delete this preset.");

		verify(presetRepository, never()).delete(any(Preset.class));
	}

	@Test
	void getPresetReturnsPresetWhenItExists() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);
		Preset preset = new Preset(
				42L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"https://cdn.example.com/presets/15/thumbnails/thumb.png");

		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));

		assertThat(presetService.getPreset(15L)).isSameAs(preset);
		verify(presetRepository).findById(15L);
		verifyNoInteractions(userRepository);
	}

	@Test
	void getPresetThrowsPresetNotFoundWhenItDoesNotExist() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);

		when(presetRepository.findById(15L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> presetService.getPreset(15L))
				.isInstanceOf(PresetNotFoundException.class)
				.hasMessage("Preset not found.");
	}

	@Test
	void getPresetsForUserReturnsPresetsOwnedByRequestedUser() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);
		Preset firstPreset = preset(11L, 77L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));
		Preset secondPreset = preset(12L, 77L, "Signal Bloom", Instant.parse("2026-03-26T16:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findAllByOwnerUserId(77L)).thenReturn(List.of(firstPreset, secondPreset));

		List<Preset> presets = presetService.getPresetsForUser(42L, 77L);

		assertThat(presets).extracting(Preset::getId).containsExactly(11L, 12L);
	}

	@Test
	void getPresetsByTagNormalizesTagNameBeforeLookup() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);
		Preset preset = preset(15L, 77L, "Glass Orbit", Instant.parse("2026-03-26T17:00:00Z"));

		when(presetRepository.findAllByTagName("ambient")).thenReturn(List.of(preset));

		List<Preset> presets = presetService.getPresetsByTag("  Ambient  ");

		assertThat(presets).extracting(Preset::getId).containsExactly(15L);
		verifyNoInteractions(userRepository);
	}

	@Test
	void attachTagToPresetSavesAssociationForAuthenticatedUser() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		PresetTagRepository presetTagRepository = mock(PresetTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, tagRepository, presetTagRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.existsById(15L)).thenReturn(true);
		when(tagRepository.existsById(7L)).thenReturn(true);
		when(presetTagRepository.existsByPresetIdAndTagId(15L, 7L)).thenReturn(false);
		when(presetTagRepository.saveAndFlush(any(PresetTag.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, PresetTag.class));

		PresetTag savedPresetTag = presetService.attachTagToPreset(42L, 15L, 7L);

		assertThat(savedPresetTag.getPresetId()).isEqualTo(15L);
		assertThat(savedPresetTag.getTagId()).isEqualTo(7L);
	}

	@Test
	void attachTagToPresetRejectsDuplicateAssociation() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		PresetTagRepository presetTagRepository = mock(PresetTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, tagRepository, presetTagRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.existsById(15L)).thenReturn(true);
		when(tagRepository.existsById(7L)).thenReturn(true);
		when(presetTagRepository.existsByPresetIdAndTagId(15L, 7L)).thenReturn(true);

		assertThatThrownBy(() -> presetService.attachTagToPreset(42L, 15L, 7L))
				.isInstanceOf(PresetTagAlreadyExistsException.class)
				.hasMessage("This tag is already attached to the preset.");
	}

	@Test
	void createPresetThumbnailUploadReturnsPresignedUploadForAuthenticatedUser() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);
		ThumbnailStorageService.PresignedThumbnailUpload presignedUpload = new ThumbnailStorageService.PresignedThumbnailUpload(
				"presets/pending/42/thumbnails/abc123.png",
				"https://upload.example.com/presets/pending/42/thumbnails/abc123.png",
				"PUT",
				Map.of("Content-Type", "image/png"),
				Instant.parse("2026-03-26T15:10:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(thumbnailStorageService.createPresetCreationUpload(42L, "thumb.png", "image/png"))
				.thenReturn(presignedUpload);

		ThumbnailStorageService.PresignedThumbnailUpload result = presetService.createPresetThumbnailUpload(
				42L,
				"thumb.png",
				"image/png",
				3L);

		assertThat(result).isEqualTo(presignedUpload);
		verify(thumbnailStorageService).createPresetCreationUpload(42L, "thumb.png", "image/png");
	}

	@Test
	void createPresetThumbnailUploadRejectsUnsupportedContentType() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);

		when(userRepository.existsById(42L)).thenReturn(true);

		assertThatThrownBy(() -> presetService.createPresetThumbnailUpload(42L, "thumb.pdf", "application/pdf", 4L))
				.isInstanceOf(InvalidThumbnailException.class)
				.hasMessage("Thumbnail must be a valid image (jpeg, png, webp, or gif).");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void createThumbnailUploadReturnsPresignedUploadForOwner() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);
		Preset preset = preset(15L, 42L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));
		ThumbnailStorageService.PresignedThumbnailUpload presignedUpload = new ThumbnailStorageService.PresignedThumbnailUpload(
				"presets/15/thumbnails/abc123.png",
				"https://upload.example.com/presets/15/thumbnails/abc123.png",
				"PUT",
				Map.of("Content-Type", "image/png"),
				Instant.parse("2026-03-26T15:10:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));
		when(thumbnailStorageService.createPresignedUpload(15L, "thumb.png", "image/png"))
				.thenReturn(presignedUpload);

		ThumbnailStorageService.PresignedThumbnailUpload result = presetService.createThumbnailUpload(
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
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);
		Preset preset = preset(15L, 42L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));

		assertThatThrownBy(() -> presetService.createThumbnailUpload(42L, 15L, "thumb.pdf", "application/pdf", 4L))
				.isInstanceOf(InvalidThumbnailException.class)
				.hasMessage("Thumbnail must be a valid image (jpeg, png, webp, or gif).");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void createThumbnailUploadRejectsOversizedUpload() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);
		Preset preset = preset(15L, 42L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));

		assertThatThrownBy(() -> presetService.createThumbnailUpload(42L, 15L, "thumb.png", "image/png", 6L * 1024 * 1024))
				.isInstanceOf(InvalidThumbnailException.class)
				.hasMessage("Thumbnail must not exceed 5 MB.");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void createThumbnailUploadRejectsNonOwner() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);
		Preset preset = preset(15L, 77L, "Not Yours", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));

		assertThatThrownBy(() -> presetService.createThumbnailUpload(42L, 15L, "thumb.png", "image/png", 3L))
				.isInstanceOf(PresetOwnershipRequiredException.class)
				.hasMessage("Preset ownership is required.");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void finalizeThumbnailUploadStoresPublicUrlAndDeletesPreviousThumbnail() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);
		Preset existingPreset = new Preset(
				42L,
				"Aurora Drift",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"https://cdn.example.com/presets/15/thumbnails/old-thumb.png");
		ReflectionTestUtils.setField(existingPreset, "id", 15L);
		ReflectionTestUtils.setField(existingPreset, "createdAt", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.of(existingPreset));
		when(thumbnailStorageService.finalizeUpload(eq(15L), eq("presets/15/thumbnails/new-thumb.png")))
				.thenReturn(new ThumbnailStorageService.FinalizedThumbnail(
						"presets/15/thumbnails/new-thumb.png",
						"https://cdn.example.com/presets/15/thumbnails/new-thumb.png"));
		when(presetRepository.saveAndFlush(any(Preset.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, Preset.class));

		Preset result = presetService.finalizeThumbnailUpload(42L, 15L, "presets/15/thumbnails/new-thumb.png");

		assertThat(result.getThumbnailRef()).isEqualTo("https://cdn.example.com/presets/15/thumbnails/new-thumb.png");
		verify(thumbnailStorageService).delete("https://cdn.example.com/presets/15/thumbnails/old-thumb.png");
	}

	@Test
	void finalizeThumbnailUploadRejectsMissingPreset() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> presetService.finalizeThumbnailUpload(42L, 15L, "presets/15/thumbnails/new-thumb.png"))
				.isInstanceOf(PresetNotFoundException.class)
				.hasMessage("Preset not found.");

		verifyNoInteractions(thumbnailStorageService);
	}

	@Test
	void finalizeThumbnailUploadRejectsNonOwner() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		ThumbnailStorageService thumbnailStorageService = mock(ThumbnailStorageService.class);
		PresetService presetService = presetServiceWithStorage(presetRepository, userRepository, thumbnailStorageService);
		Preset preset = preset(15L, 77L, "Not Yours", Instant.parse("2026-03-26T15:00:00Z"));

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));

		assertThatThrownBy(() -> presetService.finalizeThumbnailUpload(42L, 15L, "presets/15/thumbnails/new-thumb.png"))
				.isInstanceOf(PresetOwnershipRequiredException.class)
				.hasMessage("Preset ownership is required.");

		verifyNoInteractions(thumbnailStorageService);
	}

	private PresetService presetService(PresetRepository presetRepository, UserRepository userRepository) {
		return presetService(
				presetRepository,
				mock(TagRepository.class),
				mock(PresetTagRepository.class),
				userRepository);
	}

	private PresetService presetService(
			PresetRepository presetRepository,
			TagRepository tagRepository,
			PresetTagRepository presetTagRepository,
			UserRepository userRepository) {
		return new PresetService(presetRepository, tagRepository, presetTagRepository, userRepository);
	}

	private PresetService presetServiceWithStorage(
			PresetRepository presetRepository,
			UserRepository userRepository,
			ThumbnailStorageService thumbnailStorageService) {
		return new PresetService(
				presetRepository,
				mock(TagRepository.class),
				mock(PresetTagRepository.class),
				userRepository,
				thumbnailStorageService,
				thumbnailStorageProperties());
	}

	private ThumbnailStorageProperties thumbnailStorageProperties() {
		return new ThumbnailStorageProperties(
				"us-east-1",
				"mage-test-thumbnails",
				"presets",
				"https://cdn.example.com",
				"image/jpeg,image/png,image/webp,image/gif",
				5L * 1024 * 1024,
				Duration.ofMinutes(10));
	}

	private Preset preset(Long presetId, Long ownerUserId, String name, Instant createdAt) throws Exception {
		Preset preset = new Preset(
				ownerUserId,
				name,
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""));
		ReflectionTestUtils.setField(preset, "id", presetId);
		ReflectionTestUtils.setField(preset, "createdAt", createdAt);
		return preset;
	}
}
