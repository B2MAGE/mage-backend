package com.bdmage.mage_backend.dto;

import java.util.Map;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateBuilderTemplateRequest(@NotNull @PositiveOrZero Long expectedRevision,
        String name, @NotNull Map<String, Object> sceneData) { }
