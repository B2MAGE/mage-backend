package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.ModerationCapabilities;
import com.bdmage.mage_backend.dto.ModeratorUserResponse;
import com.bdmage.mage_backend.dto.UpdateModeratorRequest;
import com.bdmage.mage_backend.service.ModeratorPermissionService;
import com.bdmage.mage_backend.service.OperatorAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class ModeratorPermissionsController {
    private final OperatorAccessService access;
    private final ModeratorPermissionService permissions;
    public ModeratorPermissionsController(OperatorAccessService access, ModeratorPermissionService permissions) {
        this.access = access; this.permissions = permissions;
    }
    @GetMapping("/capabilities")
    ModerationCapabilities capabilities(@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long actor) {
        return access.capabilities(actor);
    }
    @GetMapping("/moderators/users")
    ModeratorPermissionService.Users users(@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long actor,
            @RequestParam String query) { return permissions.lookup(actor, query); }

    @PutMapping("/moderators/users/{id}")
    ModeratorUserResponse update(@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long actor,
            @PathVariable long id, @Valid @RequestBody UpdateModeratorRequest request) { return permissions.update(actor, id, request); }

    @GetMapping("/moderators/audit")
    ModeratorPermissionService.Audit audit(@RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long actor,
            @RequestParam(required = false) Long beforeId, @RequestParam(defaultValue = "20") int limit) {
        return permissions.audit(actor, beforeId, limit);
    }
}
