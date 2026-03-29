package com.bdmage.mage_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
		@NotBlank(message = "name must not be blank")
		@Size(max = 64, message = "name must be at most 64 characters")
		String name) {
}
