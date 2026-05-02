package com.bdmage.mage_backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSceneCommentRequest(
		@JsonAlias("body")
		@NotBlank(message = "text must not be blank")
		@Size(max = 2000, message = "text must be at most 2000 characters")
		String text,
		Long parentCommentId) {
}
