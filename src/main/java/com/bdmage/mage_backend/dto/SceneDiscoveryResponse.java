package com.bdmage.mage_backend.dto;

import java.util.List;

public record SceneDiscoveryResponse(
        List<SceneSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages) {
}
