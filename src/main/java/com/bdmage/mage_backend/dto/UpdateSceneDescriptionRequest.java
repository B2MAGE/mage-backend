package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.Size;

public record UpdateSceneDescriptionRequest(
		@Size(max = 1000, message = "Description must be at most 1000 characters")
		String description) {
}