package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateSceneVoteRequest(
		@NotBlank(message = "vote must not be blank")
		@Pattern(regexp = "up|down", message = "vote must be up or down")
		String vote) {
}
