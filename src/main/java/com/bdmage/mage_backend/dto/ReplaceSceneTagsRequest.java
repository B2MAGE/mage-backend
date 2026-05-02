package com.bdmage.mage_backend.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record ReplaceSceneTagsRequest(
		@NotNull(message = "tagIds must not be null")
		List<@NotNull(message = "tagIds must not contain null") Long> tagIds) {
}
