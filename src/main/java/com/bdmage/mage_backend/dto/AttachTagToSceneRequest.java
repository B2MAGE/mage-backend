package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotNull;

public record AttachTagToSceneRequest(
		@NotNull(message = "tagId must not be null")
		Long tagId) {
}
