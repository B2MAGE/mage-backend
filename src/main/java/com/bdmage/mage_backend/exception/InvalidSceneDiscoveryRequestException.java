package com.bdmage.mage_backend.exception;

import java.util.Map;

public class InvalidSceneDiscoveryRequestException extends RuntimeException {
    private final Map<String, String> details;

    public InvalidSceneDiscoveryRequestException(Map<String, String> details) {
        super("Scene discovery parameters are invalid.");
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() { return this.details; }
}
