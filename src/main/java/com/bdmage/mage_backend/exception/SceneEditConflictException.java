package com.bdmage.mage_backend.exception;

public class SceneEditConflictException extends RuntimeException {
    public SceneEditConflictException() {
        super("This scene has a newer saved version. Your draft was not saved. Keep or export your draft, then reopen the scene before saving again.");
    }
}
