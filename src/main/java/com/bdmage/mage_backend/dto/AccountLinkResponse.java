package com.bdmage.mage_backend.dto;

public record AccountLinkResponse(
		Long userId,
		String email,
		String firstName,
		String lastName,
		String displayName,
		String handle,
		String description,
		String avatarGradientStart,
		String avatarGradientEnd,
		String authProvider,
		boolean linked) {
}
