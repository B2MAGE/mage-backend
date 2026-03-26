package com.bdmage.mage_backend.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePresetRequest(
		@NotBlank(message = "name must not be blank")
		@Size(max = 100, message = "name must be at most 100 characters")
		String name,
		@NotNull(message = "sceneData must not be null")
		Map<String, Object> sceneData,
		@Size(max = 512, message = "thumbnailRef must be at most 512 characters")
		String thumbnailRef) {
}
