package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.Map;

public record BuilderTemplateResponse(Long templateId, String name, Map<String, Object> sceneData,
        String thumbnailRef, long revision, Instant createdAt, Instant updatedAt) { }
