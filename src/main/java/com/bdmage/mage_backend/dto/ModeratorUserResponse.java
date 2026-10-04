package com.bdmage.mage_backend.dto;

public record ModeratorUserResponse(long userId, String displayName, String handle, String email, boolean sceneModerator, long revision, boolean isAdministrator) {}
