package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCustomRenderingRequest(
		@NotNull(message = "enabled is required") Boolean enabled,
		@NotBlank(message = "reason must not be blank")
		@Size(max = 1000, message = "reason must be at most 1000 characters") String reason) {
}
