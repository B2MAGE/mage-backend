package com.bdmage.mage_backend.dto;

import java.util.UUID;
import java.util.Map;
import java.util.Set;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonCreator;

public record UpdateModeratorRequest(@NotNull Boolean enabled, @NotNull @Min(0) Long expectedRevision,
        @NotNull UUID requestId, @NotBlank @Size(max = 1000) String reason) {
    // Local strict binding: permission writes must not accept Jackson's scalar coercion.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static UpdateModeratorRequest fromJson(Map<String, Object> fields) {
        if (fields == null || !fields.keySet().equals(Set.of("enabled", "expectedRevision", "requestId", "reason"))
                || !(fields.get("enabled") instanceof Boolean enabled)
                || !(fields.get("reason") instanceof String reason)
                || !(fields.get("requestId") instanceof String requestId)
                || !requestId.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) {
            throw invalid();
        }
        Object revision = fields.get("expectedRevision");
        if (!(revision instanceof Integer || revision instanceof Long) || ((Number) revision).longValue() < 0) throw invalid();
        return new UpdateModeratorRequest(enabled, ((Number) revision).longValue(), UUID.fromString(requestId), reason);
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Expected a boolean permission, integer revision, UUID string and reason string only.");
    }
}
