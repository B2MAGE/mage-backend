package com.bdmage.mage_backend.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSceneRequest(
		@NotBlank(message = "name must not be blank")
		@Size(max = 100, message = "name must be at most 100 characters")
		String name,
		@Size(max = 1000, message = "description must be at most 1000 characters")
		String description,
		@NotNull(message = "sceneData must not be null")
		Map<String, Object> sceneData) {
}
