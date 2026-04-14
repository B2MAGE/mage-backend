package com.bdmage.mage_backend.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bdmage.mage_backend.dto.PresetResponse;
import com.bdmage.mage_backend.model.Preset;
import com.bdmage.mage_backend.model.User;
import com.bdmage.mage_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class PresetResponseFactory {

	private static final String UNKNOWN_CREATOR_DISPLAY_NAME = "Unknown creator";

	private final UserRepository userRepository;

	public PresetResponseFactory(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public PresetResponse from(Preset preset) {
		return PresetResponse.from(
				preset,
				this.userRepository.findById(preset.getOwnerUserId())
						.map(User::getDisplayName)
						.orElse(UNKNOWN_CREATOR_DISPLAY_NAME));
	}

	public List<PresetResponse> from(List<Preset> presets) {
		Map<Long, String> creatorDisplayNames = resolveCreatorDisplayNames(presets);

		return presets.stream()
				.map(preset -> PresetResponse.from(
						preset,
						creatorDisplayNames.getOrDefault(preset.getOwnerUserId(), UNKNOWN_CREATOR_DISPLAY_NAME)))
				.toList();
	}

	private Map<Long, String> resolveCreatorDisplayNames(List<Preset> presets) {
		Set<Long> ownerUserIds = presets.stream()
				.map(Preset::getOwnerUserId)
				.collect(java.util.stream.Collectors.toSet());

		return this.userRepository.findAllById(ownerUserIds).stream()
				.collect(java.util.stream.Collectors.toMap(User::getId, User::getDisplayName));
	}

}
