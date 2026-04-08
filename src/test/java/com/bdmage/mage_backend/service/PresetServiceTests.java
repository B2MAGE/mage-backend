package com.bdmage.mage_backend.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.exception.PresetNotFoundException;
import com.bdmage.mage_backend.exception.PresetTagAlreadyExistsException;
import com.bdmage.mage_backend.exception.TagNotFoundException;
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
				"  thumbnails/preset-1.png  ");

		ArgumentCaptor<Preset> presetCaptor = ArgumentCaptor.forClass(Preset.class);
		verify(presetRepository).saveAndFlush(presetCaptor.capture());

		Preset persistedPreset = presetCaptor.getValue();
		assertThat(persistedPreset.getOwnerUserId()).isEqualTo(42L);
		assertThat(persistedPreset.getName()).isEqualTo("Aurora Drift");
		assertThat(persistedPreset.getSceneData()).isEqualTo(this.objectMapper.readTree("""
				{"visualizer":{"shader":"nebula"}}
				"""));
		assertThat(persistedPreset.getThumbnailRef()).isEqualTo("thumbnails/preset-1.png");

		assertThat(savedPreset.getOwnerUserId()).isEqualTo(42L);
		assertThat(savedPreset.getName()).isEqualTo("Aurora Drift");
		assertThat(savedPreset.getThumbnailRef()).isEqualTo("thumbnails/preset-1.png");
	}

	@Test
	void createPresetNormalizesBlankThumbnailRefToNull() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.saveAndFlush(any(Preset.class))).thenAnswer(invocation -> invocation.getArgument(0, Preset.class));

		Preset savedPreset = presetService.createPreset(
				42L,
				"Preset Name",
				this.objectMapper.readTree("""
						{"visualizer":{"shader":"nebula"}}
						"""),
				"   ");

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
				"thumbnails/preset-1.png");

		when(presetRepository.findById(15L)).thenReturn(Optional.of(preset));

		Preset foundPreset = presetService.getPreset(15L);

		assertThat(foundPreset).isSameAs(preset);
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

		verify(presetRepository).findById(15L);
		verifyNoInteractions(userRepository);
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

		assertThat(presets)
				.extracting(Preset::getId)
				.containsExactly(11L, 12L);
		verify(userRepository).existsById(42L);
		verify(presetRepository).findAllByOwnerUserId(77L);
	}

	@Test
	void getPresetsForUserReturnsEmptyListWhenRequestedUserHasNone() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.findAllByOwnerUserId(88L)).thenReturn(List.of());

		List<Preset> presets = presetService.getPresetsForUser(42L, 88L);

		assertThat(presets).isEmpty();
		verify(userRepository).existsById(42L);
		verify(presetRepository).findAllByOwnerUserId(88L);
	}

	@Test
	void getPresetsForUserRejectsUnknownAuthenticatedUserIdentity() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);

		when(userRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> presetService.getPresetsForUser(99L, 77L))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verify(userRepository).existsById(99L);
		verify(presetRepository, never()).findAllByOwnerUserId(any());
	}

	@Test
	void getAllPresetsReturnsAllPersistedPresets() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);
		Preset firstPreset = preset(11L, 77L, "Aurora Drift", Instant.parse("2026-03-26T15:00:00Z"));
		Preset secondPreset = preset(12L, 78L, "Signal Bloom", Instant.parse("2026-03-26T16:00:00Z"));

		when(presetRepository.findAll()).thenReturn(List.of(firstPreset, secondPreset));

		List<Preset> presets = presetService.getAllPresets();

		assertThat(presets)
				.extracting(Preset::getId)
				.containsExactly(11L, 12L);
		verify(presetRepository).findAll();
		verifyNoInteractions(userRepository);
	}

	@Test
	void getPresetsByTagNormalizesTagNameBeforeLookup() throws Exception {
		PresetRepository presetRepository = mock(PresetRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, userRepository);
		Preset preset = preset(15L, 77L, "Glass Orbit", Instant.parse("2026-03-26T17:00:00Z"));

		when(presetRepository.findAllByTagName("ambient")).thenReturn(List.of(preset));

		List<Preset> presets = presetService.getPresetsByTag("  Ambient  ");

		assertThat(presets)
				.extracting(Preset::getId)
				.containsExactly(15L);
		verify(presetRepository).findAllByTagName("ambient");
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

		ArgumentCaptor<PresetTag> presetTagCaptor = ArgumentCaptor.forClass(PresetTag.class);
		verify(presetTagRepository).saveAndFlush(presetTagCaptor.capture());

		PresetTag persistedPresetTag = presetTagCaptor.getValue();
		assertThat(persistedPresetTag.getPresetId()).isEqualTo(15L);
		assertThat(persistedPresetTag.getTagId()).isEqualTo(7L);
		assertThat(savedPresetTag.getPresetId()).isEqualTo(15L);
		assertThat(savedPresetTag.getTagId()).isEqualTo(7L);
	}

	@Test
	void attachTagToPresetRejectsUnknownAuthenticatedUserIdentity() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		PresetTagRepository presetTagRepository = mock(PresetTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, tagRepository, presetTagRepository, userRepository);

		when(userRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> presetService.attachTagToPreset(99L, 15L, 7L))
				.isInstanceOf(AuthenticationRequiredException.class)
				.hasMessage("Authentication is required.");

		verify(userRepository).existsById(99L);
		verifyNoInteractions(tagRepository);
		verifyNoInteractions(presetTagRepository);
		verify(presetRepository, never()).existsById(any());
	}

	@Test
	void attachTagToPresetRejectsMissingPreset() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		PresetTagRepository presetTagRepository = mock(PresetTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, tagRepository, presetTagRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.existsById(15L)).thenReturn(false);

		assertThatThrownBy(() -> presetService.attachTagToPreset(42L, 15L, 7L))
				.isInstanceOf(PresetNotFoundException.class)
				.hasMessage("Preset not found.");

		verify(presetRepository).existsById(15L);
		verifyNoInteractions(tagRepository);
		verifyNoInteractions(presetTagRepository);
	}

	@Test
	void attachTagToPresetRejectsMissingTag() {
		PresetRepository presetRepository = mock(PresetRepository.class);
		TagRepository tagRepository = mock(TagRepository.class);
		PresetTagRepository presetTagRepository = mock(PresetTagRepository.class);
		UserRepository userRepository = mock(UserRepository.class);
		PresetService presetService = presetService(presetRepository, tagRepository, presetTagRepository, userRepository);

		when(userRepository.existsById(42L)).thenReturn(true);
		when(presetRepository.existsById(15L)).thenReturn(true);
		when(tagRepository.existsById(7L)).thenReturn(false);

		assertThatThrownBy(() -> presetService.attachTagToPreset(42L, 15L, 7L))
				.isInstanceOf(TagNotFoundException.class)
				.hasMessage("Tag not found.");

		verify(tagRepository).existsById(7L);
		verifyNoInteractions(presetTagRepository);
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

		verify(presetTagRepository).existsByPresetIdAndTagId(15L, 7L);
		verify(presetTagRepository, never()).saveAndFlush(any(PresetTag.class));
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
