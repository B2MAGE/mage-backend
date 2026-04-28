package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.Size;

public record UpdateSceneDescriptionRequest(
		@Size(max = 500, message = "Description must be at most 500 characters")
		String description) {
}