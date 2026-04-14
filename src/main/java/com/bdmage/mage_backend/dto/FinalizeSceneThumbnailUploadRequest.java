package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinalizeSceneThumbnailUploadRequest(
		@NotBlank(message = "objectKey must not be blank")
		@Size(max = 1024, message = "objectKey must be at most 1024 characters")
		String objectKey) {
}
