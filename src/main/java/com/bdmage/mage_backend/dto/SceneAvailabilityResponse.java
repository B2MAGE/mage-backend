package com.bdmage.mage_backend.dto;

public record SceneAvailabilityResponse(Long sceneId, boolean available, String code, String message) {
}
