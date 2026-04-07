package com.bdmage.mage_backend.dto;

public record AccountLinkResponse(
		Long userId,
		String email,
		String displayName,
		String authProvider,
		boolean linked) {
}
