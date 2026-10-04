package com.bdmage.mage_backend.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Administrators are explicitly configured, never inferred from moderator grants. */
@ConfigurationProperties(prefix = "mage.administration")
public record AdministratorProperties(Set<Long> userIds) {
    public AdministratorProperties {
        userIds = userIds == null ? Set.of() : Set.copyOf(userIds);
        if (userIds.stream().anyMatch(id -> id <= 0)) throw new IllegalArgumentException("Administrator IDs must be positive.");
    }
}
