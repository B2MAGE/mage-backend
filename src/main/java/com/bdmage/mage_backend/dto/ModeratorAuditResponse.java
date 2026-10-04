package com.bdmage.mage_backend.dto;

import java.time.Instant;
import java.util.UUID;

public record ModeratorAuditResponse(long id, Long administratorUserId, long targetUserId, boolean previousEnabled,
        boolean enabled, long revision, String reason, UUID requestId, Instant changedAt, String source) {}
