package com.bdmage.mage_backend.dto;

import java.util.Map;
import jakarta.validation.constraints.NotNull;

public record CreateBuilderTemplateRequest(String name,
        @NotNull Map<String, Object> sceneData) { }
