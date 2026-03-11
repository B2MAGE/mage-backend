package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
		String code,
		String message,
		Map<String, String> details,
		String path,
		Instant timestamp) {
}
