package com.bdmage.mage_backend.exception;

import java.util.Map;

/** Field-only validation diagnostics. Never includes submitted shader source. */
public class InvalidSceneDataException extends RuntimeException {

    private final Map<String, String> details;

    public InvalidSceneDataException(Map<String, String> details) {
        super("Scene data violates the submission limits policy.");
        this.details = Map.copyOf(details);
    }

    public Map<String, String> getDetails() {
        return this.details;
    }
}
