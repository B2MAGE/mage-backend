package com.bdmage.mage_backend.controller;

import com.bdmage.mage_backend.config.AuthenticatedUserRequest;
import com.bdmage.mage_backend.dto.SceneDiscoveryResponse;
import com.bdmage.mage_backend.service.SceneDiscoveryQuery;
import com.bdmage.mage_backend.service.SceneDiscoveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SceneDiscoveryController {
    private final SceneDiscoveryService discovery;

    public SceneDiscoveryController(SceneDiscoveryService discovery) { this.discovery = discovery; }

    @GetMapping("/api/scenes/discovery")
    SceneDiscoveryResponse discover(
            @RequestAttribute(name = AuthenticatedUserRequest.USER_ID_ATTRIBUTE, required = false) Long viewerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "descending") String sort,
            @RequestParam(required = false) String tag) {
        return this.discovery.discover(SceneDiscoveryQuery.from(page, size, sort, tag), viewerId);
    }
}
