package com.bdmage.mage_backend.dto;

import com.bdmage.mage_backend.model.SceneVisibility;

import jakarta.validation.constraints.NotNull;

public record UpdateSceneVisibilityRequest(
        @NotNull(message = "Visibility must not be null")
        SceneVisibility visibility) {
}