package com.bdmage.mage_backend.service;

import com.bdmage.mage_backend.exception.AuthenticationRequiredException;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.repository.PresetRepository;
import com.bdmage.mage_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
		PresetService presetService = new PresetService(presetRepository, userRepository);

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
		PresetService presetService = new PresetService(presetRepository, userRepository);

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
		PresetService presetService = new PresetService(presetRepository, userRepository);

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
		PresetService presetService = new PresetService(presetRepository, userRepository);

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
}
