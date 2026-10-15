package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.List;

/** Display metadata only. Executable scene documents belong to the detail endpoint. */
public record SceneSummaryResponse(
        Long sceneId,
        Long ownerUserId,
        String creatorDisplayName,
        String creatorHandle,
        String creatorAvatarGradientStart,
        String creatorAvatarGradientEnd,
        String name,
        String description,
        String thumbnailRef,
        Instant createdAt,
        List<String> tags,
        SceneEngagementResponse engagement,
        SceneAvailabilityResponse availability) {
}
