package com.bdmage.mage_backend.config;

import com.bdmage.mage_backend.service.ModeratorPermissionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ModeratorBootstrap implements ApplicationRunner {
    private final ModeratorPermissionService permissions;
    private final SceneAvailabilityProperties legacy;
    public ModeratorBootstrap(ModeratorPermissionService permissions, SceneAvailabilityProperties legacy) {
        this.permissions = permissions; this.legacy = legacy;
    }
    @Override public void run(ApplicationArguments arguments) { permissions.importLegacyAllowlist(legacy.operatorUserIds()); }
}
