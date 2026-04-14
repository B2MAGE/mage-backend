package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSceneThumbnailUploadRequest(
		@NotBlank(message = "filename must not be blank")
		@Size(max = 255, message = "filename must be at most 255 characters")
		String filename,
		@NotBlank(message = "contentType must not be blank")
		@Size(max = 100, message = "contentType must be at most 100 characters")
		String contentType,
		@NotNull(message = "sizeBytes must not be null")
		@Positive(message = "sizeBytes must be greater than zero")
		Long sizeBytes) {
}
