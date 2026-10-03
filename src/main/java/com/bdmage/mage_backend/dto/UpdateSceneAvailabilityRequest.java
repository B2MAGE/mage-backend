package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSceneAvailabilityRequest(
		@NotNull(message = "disabled is required") Boolean disabled,
		@NotBlank(message = "reason must not be blank")
		@Size(max = 1000, message = "reason must be at most 1000 characters") String reason) {
}
